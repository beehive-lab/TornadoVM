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

import java.util.Map;

import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.extended.UnboxNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import tornado.graal.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Graph-builder plugins for kernels written in Kotlin. They run while bytecode is parsed, before any inlining, and only
 * for methods compiled by kotlinc (see {@link KotlinSupport}).
 *
 * <ul>
 * <li>Calls to the null-check helpers of {@code kotlin.jvm.internal.Intrinsics}, which kotlinc inserts into public
 * functions, are dropped. They cannot be compiled for an accelerator, and kernel arguments are never null.</li>
 * <li>{@code Number.intValue()} (and the other {@code xxxValue()} methods) on a boxed value of the matching type, which
 * kotlinc emits to unbox a platform-typed {@code Integer}, becomes the same unboxing node as javac's
 * {@code Integer.intValue()}.</li>
 * </ul>
 */
public final class KotlinGraphBuilderPlugins {

    private static final String KOTLIN_INTRINSICS = "kotlin.jvm.internal.Intrinsics";

    private static final String NUMBER = "java.lang.Number";

    private static final Map<String, JavaKind> BOXED_TYPES = Map.of("java.lang.Integer", JavaKind.Int, //
            "java.lang.Long", JavaKind.Long, //
            "java.lang.Float", JavaKind.Float, //
            "java.lang.Double", JavaKind.Double, //
            "java.lang.Short", JavaKind.Short, //
            "java.lang.Byte", JavaKind.Byte);

    private KotlinGraphBuilderPlugins() {
    }

    /**
     * Registers the Kotlin plugins. Called by every backend when it creates its graph-builder plugins.
     *
     * @param plugins
     *     the graph-builder plugins of a backend.
     */
    public static void registerPlugins(Plugins plugins) {
        plugins.appendNodePlugin(new KotlinNodePlugin());
    }

    static boolean isNullCheckIntrinsic(ResolvedJavaMethod method) {
        if (method.getSignature().getReturnKind() != JavaKind.Void || !KOTLIN_INTRINSICS.equals(method.getDeclaringClass().toJavaName())) {
            return false;
        }
        String name = method.getName();
        return name.startsWith("checkNotNull") //
                || name.equals("checkParameterIsNotNull") //
                || name.equals("checkExpressionValueIsNotNull") //
                || name.equals("checkFieldIsNotNull") //
                || name.equals("checkReturnedValueIsNotNull");
    }

    /**
     * @return the kind to unbox to if {@code method} is {@code Number.xxxValue()} called on a boxed value of that same
     *     kind (e.g. {@code intValue()} on an {@code Integer}), otherwise null.
     */
    static JavaKind numberUnboxKind(ResolvedJavaMethod method, ValueNode receiver) {
        if (!NUMBER.equals(method.getDeclaringClass().toJavaName()) || method.getSignature().getParameterCount(false) != 0) {
            return null;
        }
        ResolvedJavaType receiverType = StampTool.typeOrNull(receiver);
        if (receiverType == null) {
            return null;
        }
        JavaKind boxedKind = BOXED_TYPES.get(receiverType.toJavaName());
        if (boxedKind == null || boxedKind != method.getSignature().getReturnKind() || !method.getName().equals(boxedKind.getJavaName() + "Value")) {
            return null;
        }
        return boxedKind;
    }

    private static final class KotlinNodePlugin implements NodePlugin {
        @Override
        public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if (!KotlinSupport.isKotlinMethod(b.getMethod())) {
                return false;
            }
            if (isNullCheckIntrinsic(method)) {
                // Returning true without pushing a value removes the (void) call from the graph.
                return true;
            }
            if (args.length == 1) {
                JavaKind kind = numberUnboxKind(method, args[0]);
                if (kind != null) {
                    b.addPush(kind, UnboxNode.create(b.getMetaAccess(), b.getConstantReflection(), b.nullCheckedValue(args[0]), kind));
                    return true;
                }
            }
            return false;
        }
    }
}
