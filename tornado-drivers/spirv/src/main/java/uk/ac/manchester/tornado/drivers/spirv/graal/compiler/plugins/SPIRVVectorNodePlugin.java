/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.SPIRVVectorValueNode;

public class SPIRVVectorNodePlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext builderContext, ResolvedJavaType type) {
        if (type.getAnnotation(Vector.class) != null) {
            return createVectorInstance(builderContext, type);
        }
        return false;
    }

    private SPIRVKind resolveSPIRVKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            return SPIRVKind.fromResolvedJavaTypeToVectorKind(type);
        }

        return SPIRVKind.ILLEGAL;
    }

    private boolean createVectorInstance(GraphBuilderContext builderContext, ResolvedJavaType type) {
        SPIRVKind spirvVectorKind = resolveSPIRVKind(type);
        if (spirvVectorKind != SPIRVKind.ILLEGAL && spirvVectorKind.isVector()) {
            builderContext.push(JavaKind.Object, builderContext.append(new SPIRVVectorValueNode(spirvVectorKind)));
            return true;
        }
        return false;
    }
}
