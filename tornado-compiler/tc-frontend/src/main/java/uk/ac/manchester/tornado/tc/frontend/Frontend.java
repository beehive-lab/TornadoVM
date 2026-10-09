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
import java.util.zip.ZipFile;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFormatException;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Loc;

/**
 * The TC Java frontend: class bytes in, pre-ABI TC-SSA out. Class bytes come from a
 * {@link BytecodeSource}, so the frontend never loads or links the kernel's class.
 */
public final class Frontend {

    /** Where class files come from (a class loader, a directory, a jar, or bytes in a test). */
    @FunctionalInterface
    public interface BytecodeSource {
        Optional<byte[]> classBytes(String internalName);

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
                return Optional.empty();
            };
        }

        /** Class files visible to {@code loader}. */
        static BytecodeSource classLoader(ClassLoader loader) {
            return internalName -> {
                try (var in = loader.getResourceAsStream(internalName + ".class")) {
                    return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
    }

    /** The translated function (or the diagnostics explaining why there is none) and the bytecode listing. */
    public record Result(Optional<Function> function, List<Diagnostic> diagnostics, String bytecode) {
        Result(Optional<Function> function, List<Diagnostic> diagnostics) {
            this(function, diagnostics, "");
        }

        public boolean succeeded() {
            return function.isPresent();
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
        var listing = Disassembler.print(classFile, method.get());
        try {
            return new Result(Optional.of(Translator.translate(classFile, method.get())), List.of(), listing);
        } catch (Translator.FrontendError e) {
            return new Result(Optional.empty(), List.of(e.diagnostic), listing);
        }
    }

    /** A kernel and every static method it calls, transitively: the kernel is the first function. */
    public record ModuleResult(Optional<uk.ac.manchester.tornado.tc.ir.IrModule> module, List<Diagnostic> diagnostics, String bytecode) {
    }

    /**
     * Translates the kernel and, transitively, the static methods it calls (for the inliner). Recursion
     * is rejected with {@code TC-E1009}: GPU kernels cannot recurse.
     */
    public static ModuleResult translateWithCallees(BytecodeSource source, String className, String methodName, String descriptor) {
        var kernel = translate(source, className, methodName, descriptor);
        if (kernel.function().isEmpty()) {
            return new ModuleResult(Optional.empty(), kernel.diagnostics(), kernel.bytecode());
        }
        var module = new uk.ac.manchester.tornado.tc.ir.IrModule();
        var work = new java.util.ArrayDeque<java.util.Map.Entry<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef>>();
        // Re-translate the kernel to learn its callees (translate() does not expose them).
        var internal = className.replace('.', '/');
        var classFile = ClassFile.parse(source.classBytes(internal).orElseThrow());
        var method = classFile.method(methodName, descriptor).orElseThrow();
        Translator.Translated root;
        try {
            root = Translator.translate(classFile, method, Function.Kind.KERNEL);
        } catch (Translator.FrontendError e) {
            return new ModuleResult(Optional.empty(), List.of(e.diagnostic), kernel.bytecode());
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
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1007", "class " + ref.owner().replace('/', '.') + " of called method " + ref.name() + " not found", "", Loc.UNKNOWN)), kernel.bytecode());
            }
            var calleeClass = ClassFile.parse(bytes.get());
            var calleeMethod = calleeClass.method(ref.name(), ref.descriptor());
            if (calleeMethod.isEmpty() || calleeMethod.get().code().isEmpty()) {
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1003", "called method " + ref.owner().replace('/', '.') + "." + ref.name() + ref.descriptor() + " has no bytecode", "", Loc.UNKNOWN)), kernel.bytecode());
            }
            try {
                var translated = Translator.translate(calleeClass, calleeMethod.get(), Function.Kind.DEVICE);
                module.add(translated.function());
                work.addAll(translated.callees().entrySet());
            } catch (Translator.FrontendError e) {
                return new ModuleResult(Optional.empty(), List.of(e.diagnostic), kernel.bytecode());
            }
            if (seen.size() > 64) {
                return new ModuleResult(Optional.empty(), List.of(new Diagnostic("TC-E1009", "more than 64 distinct callees: recursion or an unbounded call graph", "", Loc.UNKNOWN)), kernel.bytecode());
            }
        }
        return new ModuleResult(Optional.of(module), List.of(), kernel.bytecode());
    }

    private static Result failure(String code, String message) {
        return new Result(Optional.empty(), List.of(new Diagnostic(code, message, "", Loc.UNKNOWN)));
    }
}
