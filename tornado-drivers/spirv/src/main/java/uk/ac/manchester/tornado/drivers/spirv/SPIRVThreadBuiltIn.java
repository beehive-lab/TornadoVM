/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv;

import jdk.graal.compiler.graph.Node;

import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadSizeNode;

/**
 * OpenCL Thread Built-ins for SPIR-V.
 */
public enum SPIRVThreadBuiltIn {

    // @formatter:off
    GLOBAL_THREAD_ID("spirv_BuiltInGlobalInvocationId", SPIRVBuiltIn.GlobalInvocationId(), GlobalThreadIdNode.class, null),
    GLOBAL_SIZE("spirv_BuiltInGlobalSize", SPIRVBuiltIn.GlobalSize(), GlobalThreadSizeNode.class, null),
    LOCAL_THREAD_ID("spirv_BuiltInLocalInvocationId", SPIRVBuiltIn.LocalInvocationId(), LocalThreadIdFixedNode.class, LocalThreadIdNode.class),
    WORKGROUP_SIZE("spirv_BuiltInWorkgroupSize", SPIRVBuiltIn.WorkgroupSize(), LocalGroupSizeNode.class, LocalThreadSizeNode.class),
    GROUP_ID("spirv_BuiltInWorkgroupId", SPIRVBuiltIn.WorkgroupId(), GroupIdNode.class, null);
    // @formatter:on

    String name;
    SPIRVBuiltIn builtIn;
    Class<? extends Node> nodeClass;
    Class<? extends Node> optionalNodeClass;

    SPIRVThreadBuiltIn(String idName, SPIRVBuiltIn builtIn, Class<? extends Node> nodeClass, Class<? extends Node> optional) {
        this.name = idName;
        this.builtIn = builtIn;
        this.nodeClass = nodeClass;
        this.optionalNodeClass = optional;
    }

    public String getName() {
        return name;
    }

    public SPIRVBuiltIn getBuiltIn() {
        return builtIn;
    }

    public Class<? extends Node> getNodeClass() {
        return nodeClass;
    }

    public Class<? extends Node> getOptionalNodeClass() {
        return optionalNodeClass;
    }

}
