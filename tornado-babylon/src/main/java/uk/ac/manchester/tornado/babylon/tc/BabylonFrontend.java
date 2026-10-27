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
import jdk.incubator.code.dialect.core.SSA;
import jdk.incubator.code.dialect.java.JavaType;
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
        if (kernel == null || !kernel.isAnnotationPresent(Reflect.class)) {
            return Optional.empty();
        }
        boolean hat = Arrays.stream(kernel.getParameterTypes()).anyMatch(t -> t.getName().startsWith("hat."));
        if (!hat && !MODE.equals("babylon")) {
            return Optional.empty();
        }
        if (Boolean.getBoolean("tornado.tc.verbose")) {
            System.err.println("[TC] Babylon frontend: " + kernel.getDeclaringClass().getName() + "." + kernel.getName() + " from its code model");
        }
        return Optional.of(translateModule(source, loader, kernel));
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

    private static CoreOp.FuncOp lowered(Method m) {
        var model = Op.ofMethod(m).orElseThrow(() -> new BabylonTranslator.Unsupported("TC-E1007", m + " has no code model"));
        return SSA.transform(model.transform(CodeTransformer.LOWERING_TRANSFORMER));
    }

    private static Frontend.ModuleResult translateModule(Frontend.BytecodeSource source, ClassLoader loader, Method kernel) {
        var module = new IrModule();
        var pending = new ArrayDeque<Object[]>();
        try {
            var kernelName = Frontend.kernelName(kernel.getDeclaringClass().getName(), kernel.getName());
            module.add(BabylonTranslator.translate(lowered(kernel), context(loader, kernel, pending), kernelName, true, sourceFile(kernel)));
            while (!pending.isEmpty()) {
                var next = pending.poll();
                var method = (Method) next[0];
                var name = (String) next[1];
                if (module.find(name).isPresent()) {
                    continue;
                }
                if (method.isAnnotationPresent(Reflect.class)) {
                    module.add(BabylonTranslator.translate(lowered(method), context(loader, method, pending), name, false, sourceFile(method)));
                } else {
                    var owner = method.getDeclaringClass().getName().replace('.', '/');
                    var fromBytecode = Frontend.translateCallee(source, owner, method.getName(), descriptor(method), name);
                    if (fromBytecode.module().isEmpty()) {
                        return new Frontend.ModuleResult(Optional.empty(), fromBytecode.diagnostics(), "");
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
        return new Frontend.ModuleResult(Optional.of(module), List.of(), "");
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
        return new Frontend.ModuleResult(Optional.empty(), List.of(new Diagnostic(code, message, "", Loc.UNKNOWN)), "");
    }
}
