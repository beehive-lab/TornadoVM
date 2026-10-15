/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.frontend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFormatException;
import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * The TC Java frontend: class bytes in, pre-ABI TC-SSA out. Class bytes come from a
 * {@link BytecodeSource}, so the frontend never loads or links the kernel's class.
 */
public final class Frontend {

    /** Where class files come from (a class loader, a directory, a jar, or bytes in a test). */
    @FunctionalInterface
    public interface BytecodeSource {
        Optional<byte[]> classBytes(String internalName);

        /**
         * The static method a {@code static final DeviceKernel} field names (the child of
         * {@code KernelContext.launch}). Only a source backed by the host's loaded classes knows:
         * the field's value is built by the class initialiser.
         */
        default Optional<MethodRef> deviceKernel(String ownerInternalName, String field) {
            return Optional.empty();
        }

        /** The class loader behind this source, if it has one (model frontends load the kernel's class). */
        default Optional<ClassLoader> loader() {
            return Optional.empty();
        }

        /** The constants of an enum class and their primitive instance fields; host classes only. */
        default Optional<EnumInfo> enumInfo(String internalName) {
            return Optional.empty();
        }

        /**
         * The value of a primitive {@code static final} field that is not a compile-time constant
         * (javac inlines those): one the class initialiser computes, such as
         * {@code static final double TO_RADIANS = Math.PI / 180}. A kernel reads it as a constant.
         * Only a source backed by the host's classes knows it; booleans and chars come as ints.
         */
        default Optional<Number> staticFinal(String ownerInternalName, String field) {
            return Optional.empty();
        }

        /** Class files on a path of directories and jars, separated by the platform path separator. */
        static BytecodeSource classpath(String path) {
            var entries = List.of(path.split(java.io.File.pathSeparator));
            return internalName -> {
                for (var entry : entries) {
                    var root = Path.of(entry);
                    try {
                        if (Files.isDirectory(root)) {
                            var file = root.resolve(internalName + ".class");
                            if (Files.isRegularFile(file)) {
                                return Optional.of(Files.readAllBytes(file));
                            }
                        } else if (Files.isRegularFile(root)) {
                            try (var zip = new ZipFile(root.toFile())) {
                                var e = zip.getEntry(internalName + ".class");
                                if (e != null) {
                                    try (var in = zip.getInputStream(e)) {
                                        return Optional.of(in.readAllBytes());
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                // The JDK's own classes (Float.isNaN, Math.round, ...) are never on a user class path.
                return jrt(internalName);
            };
        }

        /** A class of the running JDK, from its jrt:/ image (packages map to the modules holding them). */
        static Optional<byte[]> jrt(String internalName) {
            int slash = internalName.lastIndexOf('/');
            if (slash < 0) {
                return Optional.empty();
            }
            var image = java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"));
            var packageDir = image.getPath("/packages", internalName.substring(0, slash).replace('/', '.'));
            if (!Files.isDirectory(packageDir)) {
                return Optional.empty();
            }
            try (var modules = Files.list(packageDir)) {
                for (var module : modules.toList()) {
                    var file = image.getPath("/modules", module.getFileName().toString(), internalName + ".class");
                    if (Files.isRegularFile(file)) {
                        return Optional.of(Files.readAllBytes(file));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return Optional.empty();
        }

        /** Class files visible to {@code loader}. */
        static BytecodeSource classLoader(ClassLoader loader) {
            return new BytecodeSource() {
                @Override
                public Optional<byte[]> classBytes(String internalName) {
                    try (var in = loader.getResourceAsStream(internalName + ".class")) {
                        return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public Optional<MethodRef> deviceKernel(String ownerInternalName, String field) {
                    return DeviceKernels.resolve(loader, ownerInternalName, field);
                }

                @Override
                public Optional<ClassLoader> loader() {
                    return Optional.of(loader);
                }

                private final java.util.Map<String, Optional<EnumInfo>> enums = new java.util.concurrent.ConcurrentHashMap<>();

                @Override
                public Optional<EnumInfo> enumInfo(String internalName) {
                    return enums.computeIfAbsent(internalName, n -> EnumInfo.of(loader, n));
                }

                @Override
                public Optional<Number> staticFinal(String ownerInternalName, String field) {
                    try {
                        // Initialised as the JVM would before the kernel's host code ran.
                        var f = Class.forName(ownerInternalName.replace('/', '.'), true, loader).getDeclaredField(field);
                        int m = f.getModifiers();
                        if (!java.lang.reflect.Modifier.isStatic(m) || !java.lang.reflect.Modifier.isFinal(m) || !f.getType().isPrimitive()) {
                            return Optional.empty();
                        }
                        f.setAccessible(true);
                        var v = f.get(null);
                        return Optional.of(v instanceof Boolean bool ? (bool ? 1 : 0) : v instanceof Character ch ? (int) ch : (Number) v);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        return Optional.empty();
                    }
                }
            };
        }
    }

    /**
     * An enum class: its constants in ordinal order and, per primitive instance field, each
     * constant's value (kernels see an enum value as its ordinal).
     */
    public record EnumInfo(List<String> constants, java.util.Map<String, List<Number>> fields) {

        static Optional<EnumInfo> of(ClassLoader loader, String internalName) {
            try {
                var c = Class.forName(internalName.replace('/', '.'), false, loader);
                if (!c.isEnum()) {
                    return Optional.empty();
                }
                var constants = c.getEnumConstants();
                var names = java.util.Arrays.stream(constants).map(e -> ((Enum<?>) e).name()).toList();
                var fields = new java.util.LinkedHashMap<String, List<Number>>();
                for (var f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().isPrimitive()) {
                        continue;
                    }
                    f.setAccessible(true);
                    var values = new java.util.ArrayList<Number>();
                    for (var constant : constants) {
                        var v = f.get(constant);
                        values.add(v instanceof Boolean b ? (b ? 1 : 0) : v instanceof Character ch ? (int) ch : (Number) v);
                    }
                    fields.put(f.getName(), List.copyOf(values));
                }
                return Optional.of(new EnumInfo(names, java.util.Map.copyOf(fields)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return Optional.empty();
            }
        }
    }

    /** A Java method: owner (internal name), name and descriptor. */
    public record MethodRef(String owner, String name, String descriptor) {
    }

    /**
     * The translated function (or the diagnostics explaining why there is none) and the bytecode
     * listing, printed only when asked for: only dumps and reports show it.
     */
    public record Result(Optional<Function> function, List<Diagnostic> diagnostics, Supplier<String> listing) {
        Result(Optional<Function> function, List<Diagnostic> diagnostics) {
            this(function, diagnostics, () -> "");
        }

        public boolean succeeded() {
            return function.isPresent();
        }

        public String bytecode() {
            return listing.get();
        }
    }

    private Frontend() {
    }

    /**
     * Translates {@code className.methodName} (binary class name with dots; the first method with that
     * name unless {@code descriptor} is given).
     */
    public static Result translate(BytecodeSource source, String className, String methodName, String descriptor) {
        var internal = className.replace('.', '/');
        var bytes = source.classBytes(internal);
        if (bytes.isEmpty()) {
            return failure("TC-E1007", "class " + className + " not found");
        }
        ClassFile classFile;
        try {
            classFile = ClassFile.parse(bytes.get());
        } catch (ClassFormatException e) {
            return failure("TC-E1008", "malformed class file " + className + ": " + e.getMessage());
        }
        var method = classFile.method(methodName, descriptor);
        if (method.isEmpty()) {
            return failure("TC-E1007", "method " + methodName + (descriptor == null ? "" : descriptor) + " not found in " + className);
        }
        if (method.get().code().isEmpty()) {
            // An abstract or native task method: a task built from an interface method reference (k::apply).
            return failure("TC-E1003", "task method " + className + "." + methodName + " has no bytecode (abstract or native): give the task a method with a body");
        }
        Supplier<String> listing = () -> Disassembler.print(classFile, method.get());
        try {
            return new Result(Optional.of(Translator.translate(source, classFile, method.get())), List.of(), listing);
        } catch (Translator.FrontendError e) {
            return new Result(Optional.empty(), List.of(e.diagnostic), listing);
        }
    }

    /** Code-model frontends on the class or module path (none by default). */
    private static final List<ModelFrontend> MODEL_FRONTENDS = java.util.ServiceLoader.load(ModelFrontend.class, Frontend.class.getClassLoader()).stream()
            .map(java.util.ServiceLoader.Provider::get).toList();

    /** The TC type of a JVM field descriptor, as the bytecode frontend types kernel parameters. */
    public static uk.ac.manchester.tornado.tc.ir.Type typeOf(String descriptor) {
        return Bindings.type(descriptor);
    }

    /** The name a kernel function has: the simple class name and the method name. */
    public static String kernelName(String className, String methodName) {
        var internal = className.replace('.', '/');
        return internal.substring(internal.lastIndexOf('/') + 1) + "." + methodName;
    }

    /** The name a callee has (see {@link Translator#calleeName}); {@code owner} is an internal name. */
    /** A TornadoVM API field read or call lowered by {@link #apiField} / {@link #apiCall}: its value, if any. */
    public record ApiResult(Optional<Value> value) {
    }

    /**
     * For model frontends: a field of the TornadoVM API ({@code KernelContext.globalIdx}, ...) read as the
     * bytecode frontend reads it. Empty when the field is not API.
     */
    public static Optional<ApiResult> apiField(IrBuilder b, String owner, String name) {
        return Bindings.field(owner.replace('.', '/'), name).flatMap(binding -> binding instanceof Bindings.Intrinsic(var intrinsic, var type, var immediates)
                ? Optional.of(new ApiResult(Optional.of(b.intrinsic(intrinsic, immediates, type, List.of()))))
                : Optional.empty());
    }

    /**
     * For model frontends: a call into the TornadoVM API (native arrays, {@code KernelContext},
     * {@code TornadoMath}, ...) lowered as the bytecode frontend lowers it. {@code args} include the
     * receiver of an instance call. Empty when the method is not API or its binding needs the bytecode
     * frontend's own state (vectors, atomics, half floats, device launches).
     */
    public static Optional<ApiResult> apiCall(IrBuilder b, String owner, String name, String descriptor, boolean isStatic, List<Value> args) {
        var binding = Bindings.method(owner.replace('.', '/'), name, descriptor);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        return switch (binding.get()) {
            case Bindings.Intrinsic(var intrinsic, var type, var immediates) -> {
                var result = b.intrinsic(intrinsic, immediates, type, isStatic ? args : args.subList(1, args.size()));
                yield Optional.of(new ApiResult(result.producesValue() ? Optional.of(result) : Optional.empty()));
            }
            case Bindings.ArrayGet g -> {
                // Sub-int elements widen to int, as on the JVM operand stack (char unsigned, others signed).
                Value loaded = b.arrayLoad(args.get(0), args.get(1));
                var element = loaded.type();
                if (element.equals(Type.I8) || element.equals(Type.I16)) {
                    loaded = b.convert(descriptor.endsWith(")C") ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32);
                }
                yield Optional.of(new ApiResult(Optional.of(loaded)));
            }
            case Bindings.ArraySet s -> {
                var element = args.get(0).type() instanceof Type.ArrayType(var e) ? e : args.get(2).type();
                var value = args.get(2);
                b.arrayStore(args.get(0), args.get(1), element.equals(value.type()) ? value : b.convert(ConvertOp.TRUNC, value, element));
                yield Optional.of(new ApiResult(Optional.empty()));
            }
            case Bindings.ArraySize(var type) -> Optional.of(new ApiResult(Optional.of(b.arrayLength(args.get(0), type))));
            case Bindings.Convert(var op, var result) -> Optional.of(new ApiResult(Optional.of(b.convert(op, args.get(args.size() - 1), result))));
            case Bindings.Identity i -> Optional.of(new ApiResult(Optional.of(args.get(0))));
            case Bindings.SharedArray(var element) -> args.get(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var n) && n > 0
                    ? Optional.of(new ApiResult(Optional.of(b.alloca(AddressSpace.SHARED, element, (int) n))))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    public static String calleeName(String owner, String name, String descriptor) {
        return Translator.calleeName(owner, name, descriptor);
    }

    /**
     * A callee translated from bytecode under {@code name}, with the static methods it calls in turn:
     * for a model frontend's calls into code that has no model.
     */
    public static ModuleResult translateCallee(BytecodeSource source, String owner, String methodName, String descriptor, String name) {
        var bytes = source.classBytes(owner);
        if (bytes.isEmpty()) {
            return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1007", "class " + owner.replace('/', '.') + " not found", "", Loc.UNKNOWN)), () -> "");
        }
        var classFile = ClassFile.parse(bytes.get());
        var method = classFile.method(methodName, descriptor);
        if (method.isEmpty() || method.get().code().isEmpty()) {
            return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1003", "called method " + owner.replace('/', '.') + "." + methodName + descriptor + " has no bytecode", "", Loc.UNKNOWN)), () -> "");
        }
        var module = new uk.ac.manchester.tornado.tc.ir.IrModule();
        try {
            var translated = Translator.translateDevice(source, classFile, method.get(), name);
            module.add(translated.function());
            for (var callee : translated.callees().entrySet()) {
                var ref = callee.getValue();
                var inner = translateCallee(source, ref.owner(), ref.name(), ref.descriptor(), callee.getKey());
                if (inner.module().isEmpty()) {
                    return inner;
                }
                inner.module().get().functions().stream().filter(f -> module.find(f.name()).isEmpty()).forEach(module::add);
            }
        } catch (Translator.FrontendError e) {
            return new ModuleResult(Optional.empty(), List.of(e.diagnostic), () -> "");
        }
        return new ModuleResult(Optional.of(module), List.of(), () -> "");
    }

    /** A kernel and every static method it calls, transitively: the kernel is the first function. */
    public record ModuleResult(Optional<uk.ac.manchester.tornado.tc.ir.IrModule> module, List<Diagnostic> diagnostics, Supplier<String> listing) {
        public String bytecode() {
            return listing.get();
        }
    }

    /**
     * Translates the kernel and, transitively, the static methods it calls (for the inliner). Recursion
     * is rejected with {@code TC-E1009}: GPU kernels cannot recurse.
     */
    public static ModuleResult translateWithCallees(BytecodeSource source, String className, String methodName, String descriptor) {
        for (var model : MODEL_FRONTENDS) {
            var result = model.translate(source, className, methodName, descriptor);
            if (result.isPresent()) {
                return result.get();
            }
        }
        var kernel = translate(source, className, methodName, descriptor);
        if (kernel.function().isEmpty()) {
            return new ModuleResult(Optional.empty(), kernel.diagnostics(), kernel.listing());
        }
        var module = new uk.ac.manchester.tornado.tc.ir.IrModule();
        var work = new java.util.ArrayDeque<java.util.Map.Entry<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef>>();
        // Re-translate the kernel to learn its callees (translate() does not expose them).
        var internal = className.replace('.', '/');
        var classFile = ClassFile.parse(source.classBytes(internal).orElseThrow());
        var method = classFile.method(methodName, descriptor).orElseThrow();
        Translator.Translated root;
        try {
            root = Translator.translate(source, classFile, method, Function.Kind.KERNEL);
        } catch (Translator.FrontendError e) {
            return new ModuleResult(Optional.empty(), List.of(e.diagnostic), kernel.listing());
        }
        module.add(root.function());
        work.addAll(root.callees().entrySet());
        var seen = new java.util.HashSet<String>();
        while (!work.isEmpty()) {
            var entry = work.poll();
            if (!seen.add(entry.getKey())) {
                continue;
            }
            var ref = entry.getValue();
            var bytes = source.classBytes(ref.owner());
            if (bytes.isEmpty()) {
                var why = "class " + ref.owner().replace('/', '.') + " of called method " + ref.name() + " not found";
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1007", why, "", Loc.UNKNOWN)), kernel.listing());
            }
            var calleeClass = ClassFile.parse(bytes.get());
            var calleeMethod = calleeClass.method(ref.name(), ref.descriptor());
            // An inherited instance method is declared in a superclass.
            while (calleeMethod.isEmpty() && calleeClass.superName().isPresent()) {
                var superBytes = source.classBytes(calleeClass.superName().get());
                if (superBytes.isEmpty()) {
                    break;
                }
                calleeClass = ClassFile.parse(superBytes.get());
                calleeMethod = calleeClass.method(ref.name(), ref.descriptor());
            }
            if (calleeMethod.isEmpty() || calleeMethod.get().code().isEmpty()) {
                var why = "called method " + ref.owner().replace('/', '.') + "." + ref.name() + ref.descriptor() + " has no bytecode";
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1003", why, "", Loc.UNKNOWN)), kernel.listing());
            }
            try {
                var translated = Translator.translateDevice(source, calleeClass, calleeMethod.get(), entry.getKey());
                module.add(translated.function());
                work.addAll(translated.callees().entrySet());
            } catch (Translator.FrontendError e) {
                return new ModuleResult(Optional.empty(), List.of(e.diagnostic), kernel.listing());
            }
            if (seen.size() > 64) {
                var why = "more than 64 distinct callees: recursion or an unbounded call graph";
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1009", why, "", Loc.UNKNOWN)), kernel.listing());
            }
        }
        return new ModuleResult(Optional.of(module), List.of(), kernel.listing());
    }

    private static Result failure(String code, String message) {
        return new Result(Optional.empty(), List.of(new Diagnostic(code, message, "", Loc.UNKNOWN)));
    }
}
