/*
 * Copyright (c) 2023, 2024 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc;

import java.util.Optional;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;

/**
 * Compiler phase to set the position in the Panama Object header in which the Array Size will be located.
 * By default, we set the size in position 0 of the input array. The array header size is determined by the TornadoVM API.
 */
public class TornadoPanamaSegmentsHeaderPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (ReadNode readNode : graph.getNodes().filter(ReadNode.class)) {
            LocationIdentity locationIdentity = (LocationIdentity) readNode.getLocationIdentity();
            String methodName = locationIdentity.toString();
            if (methodName.endsWith("numberOfElements")) {
                AddressNode address = readNode.getAddress();
                if (address instanceof OffsetAddressNode offsetAddressNode) {
                    ValueNode offset = offsetAddressNode.getOffset();
                    if (offset instanceof ConstantNode) {
                        // Reserve the position 0 of the array to set the array size.
                        ConstantNode constantNode = graph.addOrUnique(ConstantNode.forLong((0)));
                        offsetAddressNode.setOffset(constantNode);
                        offset.safeDelete();
                    }
                }
            }
        }
    }
}
