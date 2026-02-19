/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.Phase;

import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayCopyNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FixedArrayNode;

import java.util.Optional;

/**
 * This phase examines if a copy takes place between two arrays in private memory based on
 * an if condition and, if so, inserts a {@link FixedArrayCopyNode} to generate an update in the references.
 */
public class TornadoFixedArrayCopyPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {
        for (ValuePhiNode phiNode : graph.getNodes().filter(ValuePhiNode.class)) {
            if (isFixedArrayCopied(phiNode)) {
                FixedArrayNode fixedArrayNode = phiNode.values().filter(FixedArrayNode.class).first();
                ResolvedJavaType resolvedJavaType = fixedArrayNode.getElementType();
                OCLArchitecture.OCLMemoryBase oclMemoryBase = fixedArrayNode.getMemoryRegister();
                OffsetAddressNode offsetAddressNode = phiNode.usages().filter(OffsetAddressNode.class).first();
                FixedArrayCopyNode fixedArrayCopyNode = new FixedArrayCopyNode(phiNode, resolvedJavaType, oclMemoryBase);
                graph.addWithoutUnique(fixedArrayCopyNode);
                offsetAddressNode.replaceFirstInput(phiNode, fixedArrayCopyNode);
                // finally, since we know that the data accessed is a fixed array, fix the offset
                ValuePhiNode privateIndex = getPrivateArrayIndex(offsetAddressNode.getOffset());
                if (privateIndex == null) {
                    throw new TornadoCompilationException("Index of FixedArrayNode is null.");
                }
                offsetAddressNode.setOffset(privateIndex);
            }
        }
    }

    private static boolean isFixedArrayCopied(ValuePhiNode phiNode) {
        return phiNode.usages().filter(OffsetAddressNode.class).isNotEmpty() && phiNode.values().filter(FixedArrayNode.class).isNotEmpty();
    }

    private static ValuePhiNode getPrivateArrayIndex(Node node) {
        // identify the index
        for (Node input : node.inputs()) {
            if (input instanceof ValuePhiNode phiNode) {
                return phiNode;
            } else {
                return getPrivateArrayIndex(input);
            }
        }
        return null;
    }

}
