/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLowTierContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;

import java.util.Optional;

public class TornadoFixedArrayCopyPhase extends BasePhase<TornadoLowTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoLowTierContext context) {
        for (ValuePhiNode phiNode : graph.getNodes().filter(ValuePhiNode.class)) {
            if (isFixedArrayCopied(phiNode)) {
                throw new TornadoCompilationException("Copying a local array by reference is not currently supported for SPIR-V.");
            }
        }
    }

    private static boolean isFixedArrayCopied(ValuePhiNode phiNode) {
        return phiNode.usages().filter(OffsetAddressNode.class).isNotEmpty() && phiNode.values().filter(FixedArrayNode.class).isNotEmpty();
    }

}