/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorValueNode;

public class PTXVectorNodePlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {

        if (type.getAnnotation(Vector.class) != null) {
            return createVectorInstance(b, type);
        }

        return false;

    }

    private boolean createVectorInstance(GraphBuilderContext b, ResolvedJavaType type) {
        PTXKind vectorKind = resolvePTXKind(type);
        if (vectorKind != PTXKind.ILLEGAL) {
            b.push(JavaKind.Object, b.append(new VectorValueNode(vectorKind)));
            return true;
        }

        return false;
    }

    private PTXKind resolvePTXKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            return PTXKind.fromResolvedJavaType(type);
        }

        return PTXKind.ILLEGAL;
    }

}
