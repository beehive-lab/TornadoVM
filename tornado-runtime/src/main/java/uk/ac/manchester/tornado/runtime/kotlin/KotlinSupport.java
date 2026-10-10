/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.kotlin;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tornado.graal.compiler.bytecode.BytecodeStream;
import tornado.graal.compiler.bytecode.Bytecodes;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Decides whether the Kotlin-specific compiler support applies to a method.
 *
 * <p>
 * Every Kotlin-specific rewrite in the compiler is guarded by {@link #isKotlinMethod}: it only applies to methods whose
 * declaring class was compiled by kotlinc (kotlinc annotates every class it emits with {@code kotlin.Metadata}), and
 * only while {@link TornadoOptions#KOTLIN_SUPPORT} ({@code -Dtornado.kotlin.support}) is enabled. Java methods always
 * take the existing code paths.
 * </p>
 *
 * <p>
 * The annotation is matched by name, so the runtime does not depend on the Kotlin standard library.
 * </p>
 */
public final class KotlinSupport {

    private static final String KOTLIN_METADATA = "kotlin.Metadata";

    private static final String KOTLIN_INTRINSICS = "kotlin.jvm.internal.Intrinsics";

    private static final Map<ResolvedJavaType, Boolean> KOTLIN_TYPES = new ConcurrentHashMap<>();

    private KotlinSupport() {
    }

    /**
     * @param method
     *     a method being compiled.
     * @return true if Kotlin support is enabled and {@code method} was compiled by kotlinc.
     */
    public static boolean isKotlinMethod(ResolvedJavaMethod method) {
        return TornadoOptions.KOTLIN_SUPPORT && method != null && isKotlinType(method.getDeclaringClass());
    }

    /**
     * Parameter annotations of a task method, such as {@code @Reduce}.
     *
     * <p>
     * A Kotlin function reference passed as a task ({@code task("t0", ::reduce, ...)}) is compiled to a synthetic static
     * method that only forwards its arguments to the referenced function, and that does not copy the function's
     * parameter annotations. For such a forwarding method this returns the annotations of the function it calls. For
     * any other method, and for all Java methods, it returns {@code method.getParameterAnnotations()}.
     * </p>
     *
     * @param method
     *     the task method.
     * @return the parameter annotations that apply to {@code method}.
     */
    public static Annotation[][] getParameterAnnotations(ResolvedJavaMethod method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        if (!isKotlinMethod(method) || hasAnyAnnotation(annotations)) {
            return annotations;
        }
        ResolvedJavaMethod target = getForwardingTarget(method);
        return target != null ? target.getParameterAnnotations() : annotations;
    }

    /**
     * The method whose code a task actually runs. For a Kotlin forwarding method (see
     * {@link #getParameterAnnotations}), that is the function it forwards to; for any other method, and for all Java
     * methods, it is {@code method} itself.
     *
     * @param method
     *     the task method.
     * @return the method to analyse in place of {@code method}.
     */
    public static ResolvedJavaMethod resolveForwardedMethod(ResolvedJavaMethod method) {
        if (!isKotlinMethod(method) || hasAnyAnnotation(method.getParameterAnnotations())) {
            return method;
        }
        ResolvedJavaMethod target = getForwardingTarget(method);
        return target != null ? target : method;
    }

    private static boolean hasAnyAnnotation(Annotation[][] annotations) {
        for (Annotation[] parameterAnnotations : annotations) {
            if (parameterAnnotations.length > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the static method that {@code method} forwards its arguments to, if {@code method} is static and its only
     *     call (other than kotlinc's null checks) is to a static method with the same parameter types; otherwise null.
     */
    private static ResolvedJavaMethod getForwardingTarget(ResolvedJavaMethod method) {
        byte[] code = method.getCode();
        if (!method.isStatic() || code == null) {
            return null;
        }
        ResolvedJavaMethod target = null;
        BytecodeStream stream = new BytecodeStream(code);
        while (stream.currentBC() != Bytecodes.END) {
            int opcode = stream.currentBC();
            if (opcode == Bytecodes.INVOKESTATIC) {
                JavaMethod callee = method.getConstantPool().lookupMethod(stream.readCPI(), opcode, method);
                if (!(callee instanceof ResolvedJavaMethod resolvedCallee)) {
                    return null;
                }
                if (!KOTLIN_INTRINSICS.equals(resolvedCallee.getDeclaringClass().toJavaName())) {
                    if (target != null) {
                        return null;
                    }
                    target = resolvedCallee;
                }
            } else if (opcode == Bytecodes.INVOKEVIRTUAL || opcode == Bytecodes.INVOKESPECIAL || opcode == Bytecodes.INVOKEINTERFACE || opcode == Bytecodes.INVOKEDYNAMIC) {
                return null;
            }
            stream.next();
        }
        return target != null && sameParameterTypes(method, target) ? target : null;
    }

    private static boolean sameParameterTypes(ResolvedJavaMethod a, ResolvedJavaMethod b) {
        Signature sa = a.getSignature();
        Signature sb = b.getSignature();
        if (sa.getParameterCount(false) != sb.getParameterCount(false)) {
            return false;
        }
        for (int i = 0; i < sa.getParameterCount(false); i++) {
            if (!sa.getParameterType(i, null).getName().equals(sb.getParameterType(i, null).getName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isKotlinType(ResolvedJavaType type) {
        return KOTLIN_TYPES.computeIfAbsent(type, KotlinSupport::hasKotlinMetadata);
    }

    private static boolean hasKotlinMetadata(ResolvedJavaType type) {
        try {
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                if (KOTLIN_METADATA.equals(annotation.annotationType().getName())) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // Types whose annotations cannot be read (e.g. synthetic or array types) are not Kotlin classes.
        }
        return false;
    }
}
