/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringByNodePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

public class SPIRVAddressLowering extends AddressLoweringByNodePhase.AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        SPIRVArchitecture.SPIRVMemoryBase memoryRegister = SPIRVArchitecture.kernelContextSpace;
        if (base instanceof FixedArrayNode) {
            memoryRegister = ((FixedArrayNode) base).getMemoryRegister();
        } else if (base instanceof LocalArrayNode) {
            memoryRegister = ((LocalArrayNode) base).getMemoryRegister();
        } else if (!((base instanceof TornadoAddressArithmeticNode) || (base instanceof ParameterNode) || (base instanceof ReadNode) || (base instanceof FloatingReadNode)
                || (base instanceof PiNode))) {
            TornadoInternalError.unimplemented("address origin unimplemented: %s", base.getClass().getName());
        }

        SPIRVAddressNode result = new SPIRVAddressNode(base, offset, memoryRegister);
        return base.graph().unique(result);
    }
}
