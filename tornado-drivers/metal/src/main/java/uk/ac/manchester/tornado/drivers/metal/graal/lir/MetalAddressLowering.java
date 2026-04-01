/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture.MetalMemoryBase;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.FixedArrayCopyNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

public class MetalAddressLowering extends AddressLoweringByNodePhase.AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        MetalMemoryBase memoryRegister = MetalArchitecture.globalSpace;
        if (base instanceof FixedArrayNode fixedArrayNode) {
            memoryRegister = fixedArrayNode.getMemoryRegister();
        } else if (base instanceof FixedArrayCopyNode fixedArrayCopyNode) {
            memoryRegister = fixedArrayCopyNode.getMemoryRegister();
        } else if (base instanceof LocalArrayNode localArrayNode) {
            memoryRegister = localArrayNode.getMemoryRegister();
        } else if (!((base instanceof TornadoAddressArithmeticNode) || (base instanceof ParameterNode) || (base instanceof ReadNode) || (base instanceof FloatingReadNode) || (base instanceof PiNode))) {
            TornadoInternalError.unimplemented("address origin unimplemented: %s", base.getClass().getName());
        }

        MetalAddressNode result = new MetalAddressNode(base, offset, memoryRegister);
        return (base.graph().unique(result));
    }
}
