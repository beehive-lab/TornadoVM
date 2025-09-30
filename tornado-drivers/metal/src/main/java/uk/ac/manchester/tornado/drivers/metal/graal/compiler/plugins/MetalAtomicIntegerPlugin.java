/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.TornadoAtomicIntegerNode;

public class MetalAtomicIntegerPlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        if (type.getAnnotation(Vector.class) != null) {
            return false;
        }
        return createAtomicIntegerInstance(b, type);
    }

    private boolean createAtomicIntegerInstance(GraphBuilderContext b, ResolvedJavaType type) {
        MetalKind kind = resolveMetalKind(type);
        if (kind != MetalKind.ILLEGAL) {
            if (kind == MetalKind.INTEGER_ATOMIC_JAVA) {
                b.push(JavaKind.Object, b.append(new TornadoAtomicIntegerNode(kind)));
                return true;
            }
        }
        return false;
    }

    private MetalKind resolveMetalKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            return MetalKind.fromResolvedJavaType(type);
        }
        return MetalKind.ILLEGAL;
    }
}
