/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.java.LoadFieldNode;

/**
 * Folds an enum-valued argument of an invocation plugin to the enum constant itself.
 *
 * <p>
 * Both the tensor-core intrinsics (an {@code MMAShape} operand) and the tile intrinsics (a
 * {@code DType} operand) need this, and neither can take the direct route: an enum reference is
 * a {@code getstatic} that is only constant-folded in a later phase, so it arrives as a
 * {@link LoadFieldNode}, and {@code SnippetReflection.asObject()} is unimplemented in TornadoVM,
 * so the object cannot be materialised. What works is reading the {@code ordinal} field that
 * every enum inherits from {@link Enum} and indexing into the constant array.
 * </p>
 */
final class CUDAEnumFolding {

    private CUDAEnumFolding() {
    }

    /**
     * @param b
     *     graph builder context, for constant reflection and meta access
     * @param node
     *     the argument node to fold
     * @param enumClass
     *     the enum type being folded
     * @param requirement
     *     message describing what has to be a compile-time constant, used verbatim when the
     *     argument does not fold, so each intrinsic keeps its own actionable wording
     * @return the enum constant the argument denotes
     */
    static <E extends Enum<E>> E resolveEnumConstant(GraphBuilderContext b, ValueNode node, Class<E> enumClass, String requirement) {
        JavaConstant constant = node.asJavaConstant();
        if (constant == null && node instanceof LoadFieldNode load && load.field().isStatic()) {
            constant = b.getConstantReflection().readFieldValue(load.field(), null);
        }
        if (constant == null || constant.isNull()) {
            throw new IllegalStateException(requirement);
        }

        ResolvedJavaType enumType = b.getMetaAccess().lookupJavaType(enumClass);
        ResolvedJavaField ordinalField = null;
        for (ResolvedJavaField field : enumType.getInstanceFields(true)) {
            if (field.getName().equals("ordinal")) {
                ordinalField = field;
                break;
            }
        }
        if (ordinalField == null) {
            throw new IllegalStateException("Cannot locate Enum.ordinal field on " + enumClass.getSimpleName());
        }

        JavaConstant ordinal = b.getConstantReflection().readFieldValue(ordinalField, constant);
        if (ordinal == null) {
            throw new IllegalStateException("Failed to read ordinal of a " + enumClass.getSimpleName() + " constant");
        }
        return enumClass.getEnumConstants()[ordinal.asInt()];
    }
}
