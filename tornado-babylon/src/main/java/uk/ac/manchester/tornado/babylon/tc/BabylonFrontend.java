/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jdk.incubator.code.CodeTransformer;
import jdk.incubator.code.Op;
import jdk.incubator.code.Reflect;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.core.Inliner;
import jdk.incubator.code.dialect.core.SSA;
import jdk.incubator.code.dialect.java.JavaOp;
import jdk.incubator.code.dialect.java.JavaType;
import jdk.incubator.code.dialect.java.MethodRef;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.TypeAnnotations;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.frontend.ModelFrontend;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.IrModule;
import uk.ac.manchester.tornado.tc.ir.Loc;

/**
 * TC's frontend from Babylon code models ({@code jdk.incubator.code}). It takes a kernel when the
 * method is {@code @Reflect} and either has HAT parameters ({@code hat.*}: HAT kernels have no other
 * way in) or {@code -Dtornado.tc.frontend=babylon} asks for code models; otherwise the bytecode
 * frontend keeps it ({@code -Dtornado.tc.frontend=bytecode} forces that).
 *
 * <p>The model is lowered and put in SSA form, then translated op by op ({@link BabylonTranslator}).
 * Callees with a model ({@code @Reflect}) are translated the same way; callees without one come from
 * their bytecode.</p>
 */
public final class BabylonFrontend implements ModelFrontend {

    private static final String MODE = System.getProperty("tornado.tc.frontend", "auto");

    @Override
    public Optional<Frontend.ModuleResult> translate(Frontend.BytecodeSource source, String className, String methodName, String descriptor) {
        if (MODE.equals("bytecode") || source.loader().isEmpty()) {
            return Optional.empty();
        }
        var loader = source.loader().get();
        Method kernel;
        try {
            kernel = find(Class.forName(className, false, loader), methodName, descriptor);
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
        if (kernel == null || !(kernel.isAnnotationPresent(Reflect.class) || MODE.equals("babylon") && Op.ofMethod(kernel).isPresent())) {
            return Optional.empty();
        }
        if (!MODE.equals("babylon") && !isHat(kernel)) {
            return Optional.empty();
        }
        if (Boolean.getBoolean("tornado.tc.verbose")) {
            System.err.println("[TC] Babylon frontend: " + kernel.getDeclaringClass().getName() + "." + kernel.getName() + " from its code model");
        }
        return Optional.of(translateModule(source, loader, kernel));
    }

    /**
     * A HAT kernel: a parameter is a HAT buffer or other mapped interface (user buffers, such as a
     * {@code ComplexArray}, live in the user's package), or the kernel calls into HAT ({@code GIX()}).
     */
    private static boolean isHat(Method kernel) {
        for (var t : kernel.getParameterTypes()) {
            if (t.getName().startsWith("hat.") || implementsByName(t, "optkl.ifacemapper.MappableIface")) {
                return true;
            }
        }
        return Op.ofMethod(kernel).map(model -> model.elements().anyMatch(e -> e instanceof JavaOp.InvokeOp invoke
                && invoke.invokeReference().refType().toString().startsWith("hat."))).orElse(false);
    }

    private static boolean implementsByName(Class<?> c, String name) {
        if (c == null) {
            return false;
        }
        if (c.getName().equals(name)) {
            return true;
        }
        for (var i : c.getInterfaces()) {
            if (implementsByName(i, name)) {
                return true;
            }
        }
        return implementsByName(c.getSuperclass(), name);
    }

    private static Method find(Class<?> owner, String name, String descriptor) {
        for (var m : owner.getDeclaredMethods()) {
            if (m.getName().equals(name) && (descriptor == null || descriptor(m).equals(descriptor))) {
                return m;
            }
        }
        return null;
    }

    private static String descriptor(Method m) {
        return MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString();
    }

    private static CoreOp.FuncOp lowered(Frontend.BytecodeSource source, Method m) {
        var model = Op.ofMethod(m).orElseThrow(() -> new BabylonTranslator.Unsupported("TC-E1007", m + " has no code model"));
        var lowered = inlineTileHelpers(m, model.transform(CodeTransformer.LOWERING_TRANSFORMER), 0);
        var parallel = parallelLocals(source, m);
        if (!parallel.isEmpty()) {
            lowered = lowered.transform((builder, op) -> {
                if (op instanceof CoreOp.VarOp var && parallel.contains(var.varName()) && !var.isUninitialized()
                        && (var.varValueType().equals(JavaType.INT) || var.varValueType().equals(JavaType.LONG))) {
                    var marker = MethodRef.method(Markers.class, "parallel", var.varValueType().equals(JavaType.INT) ? int.class : long.class,
                            var.varValueType().equals(JavaType.INT) ? int.class : long.class);
                    var marked = builder.add(JavaOp.invoke(marker, builder.context().getValue(var.initOperand())));
                    builder.context().mapValue(var.result(), builder.add(CoreOp.var(var.varName(), var.varValueType(), marked)));
                    return builder;
                }
                builder.add(op);
                return builder;
            });
        }
        var ssa = SSA.transform(lowered);
        if (Boolean.getBoolean("tornado.babylon.dumpModels")) {
            System.err.println("[TC] Babylon code model of " + m + ", lowered, as TC reads it:\n" + ssa.toText());
        }
        return ssa;
    }

    private static boolean isTile(jdk.incubator.code.CodeType type) {
        return type instanceof JavaType j && !j.toNominalDescriptor().isPrimitive() && j.toNominalDescriptor().descriptorString().equals("Lhat/types/Tile;");
    }

    /**
     * HAT helpers that take or return tiles are inlined into their callers (in the code model): a
     * tile's shape is part of its CUDA Tile type, and a store needs it, so tiles do not cross calls.
     */
    private static CoreOp.FuncOp inlineTileHelpers(Method owner, CoreOp.FuncOp lowered, int depth) {
        if (depth > 8 || lowered.elements().noneMatch(e -> e instanceof JavaOp.InvokeOp invoke && takesTiles(invoke))) {
            return lowered;
        }
        var inlined = lowered.transform((builder, op) -> {
            if (op instanceof JavaOp.InvokeOp invoke && takesTiles(invoke)) {
                Method callee;
                try {
                    var lookup = java.lang.invoke.MethodHandles.privateLookupIn(owner.getDeclaringClass(), java.lang.invoke.MethodHandles.lookup());
                    callee = invoke.invokeReference().resolveToMethod(lookup);
                } catch (ReflectiveOperationException e) {
                    throw new BabylonTranslator.Unsupported("TC-E1007", "tile helper " + invoke.invokeReference() + " cannot be resolved: " + e);
                }
                var model = Op.ofMethod(callee).orElseThrow(() -> new BabylonTranslator.Unsupported("TC-E1007", callee + " takes tiles but has no code model (@Reflect)"));
                var args = invoke.operands().stream().map(builder.context()::getValue).toList();
                var after = Inliner.inline(builder, model.transform(CodeTransformer.LOWERING_TRANSFORMER), args);
                if (invoke.result() != null && !after.parameters().isEmpty()) {
                    builder.context().mapValue(invoke.result(), after.parameters().get(0));
                }
                return after;
            }
            builder.add(op);
            return builder;
        });
        return inlineTileHelpers(owner, inlined, depth + 1);
    }

    private static boolean takesTiles(JavaOp.InvokeOp invoke) {
        var ref = invoke.invokeReference();
        var owner = ref.refType().toString();
        if (owner.equals("hat.TileContext") || owner.equals("hat.types.Tile")) {
            return false; // the tile operations themselves
        }
        var signature = ref.signature();
        return isTile(signature.returnType()) || signature.parameterTypes().stream().anyMatch(BabylonFrontend::isTile);
    }

    /** Names of the method's locals annotated {@code @Parallel}, from its class file (code models do not keep them). */
    private static java.util.Set<String> parallelLocals(Frontend.BytecodeSource source, Method m) {
        var owner = m.getDeclaringClass().getName().replace('.', '/');
        var classFile = source.classFile(owner);
        if (classFile.isEmpty()) {
            return java.util.Set.of();
        }
        var code = classFile.get().method(m.getName(), descriptor(m)).flatMap(ClassFile.Method::code);
        if (code.isEmpty() || code.get().typeAnnotations() == null) {
            return java.util.Set.of();
        }
        var names = new java.util.HashSet<String>();
        for (var a : TypeAnnotations.locals(code.get().typeAnnotations(), classFile.get().constants())) {
            if (!a.descriptor().equals("Luk/ac/manchester/tornado/api/annotations/Parallel;")) {
                continue;
            }
            for (var local : code.get().locals()) {
                if (local.slot() == a.slot() && local.startBci() < a.startBci() + a.length() && a.startBci() < local.startBci() + local.length()) {
                    names.add(local.name());
                }
            }
        }
        return names;
    }

    private static Frontend.ModuleResult translateModule(Frontend.BytecodeSource source, ClassLoader loader, Method kernel) {
        var module = new IrModule();
        var pending = new ArrayDeque<Object[]>();
        try {
            var kernelName = Frontend.kernelName(kernel.getDeclaringClass().getName(), kernel.getName());
            module.add(BabylonTranslator.translate(lowered(source, kernel), context(loader, kernel, pending), kernelName, true, sourceFile(kernel)));
            while (!pending.isEmpty()) {
                var next = pending.poll();
                var method = (Method) next[0];
                var name = (String) next[1];
                if (module.find(name).isPresent()) {
                    continue;
                }
                if (method.isAnnotationPresent(Reflect.class)) {
                    module.add(BabylonTranslator.translate(lowered(source, method), context(loader, method, pending), name, false, sourceFile(method)));
                } else {
                    var owner = method.getDeclaringClass().getName().replace('.', '/');
                    var fromBytecode = Frontend.translateCallee(source, owner, method.getName(), descriptor(method), name);
                    if (fromBytecode.module().isEmpty()) {
                        return new Frontend.ModuleResult(Optional.empty(), fromBytecode.diagnostics(), () -> "");
                    }
                    fromBytecode.module().get().functions().stream().filter(f -> module.find(f.name()).isEmpty()).forEach(module::add);
                }
                if (module.functions().size() > 64) {
                    return failure("TC-E1009", "more than 64 distinct callees: recursion or an unbounded call graph");
                }
            }
        } catch (BabylonTranslator.Unsupported e) {
            return failure(e.code, e.getMessage());
        } catch (RuntimeException e) {
            return failure("TC-E9003", "Babylon frontend: " + e);
        }
        return new Frontend.ModuleResult(Optional.of(module), List.of(), () -> Op.ofMethod(kernel).map(m -> m.toText()).orElse(""));
    }

    private static BabylonTranslator.Context context(ClassLoader loader, Method function, ArrayDeque<Object[]> pending) {
        return new BabylonTranslator.Context() {
            @Override
            public Class<?> load(JavaType type) {
                try {
                    return Class.forName(type.erasure().toNominalDescriptor().descriptorString().replaceAll("^L|;$", "").replace('/', '.'), false, loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    return null;
                }
            }

            @Override
            public java.lang.foreign.GroupLayout bufferLayout(int index) {
                return HatLayouts.layout(function, index);
            }

            @Override
            public void callee(Method method, String name) {
                pending.add(new Object[] { method, name });
            }
        };
    }

    private static String sourceFile(Method m) {
        var simple = m.getDeclaringClass().getName();
        simple = simple.substring(simple.lastIndexOf('.') + 1);
        return (simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple) + ".java";
    }

    private static Frontend.ModuleResult failure(String code, String message) {
        return new Frontend.ModuleResult(Optional.empty(), List.of(new Diagnostic(code, message, "", Loc.UNKNOWN)), () -> "");
    }
}
