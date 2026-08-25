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
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import java.util.Optional;

import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalFieldAddressArithmeticNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

/**
 * This phase identifies if a {@code TornadoAddressArithmeticNode} is associated with
 * a {@code MetalDecompressedReadFieldNode}. If this is the case, the {@code TornadoAddressArithmeticNode}
 * is replaced by a {@code MetalFieldAddressArithmeticNode}, which emits the absolute address, as
 * evaluated by the corresponding {@code MetalDecompressedReadFieldNode}. Mirrors
 * {@code OCLFieldCoopsAccessPhase}.
 */
public class MetalFieldCoopsAccessPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (MetalDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(MetalDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                MetalFieldAddressArithmeticNode metalFieldAddressArithmetic = new MetalFieldAddressArithmeticNode(readDecompressedField);
                graph.addWithoutUnique(metalFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(metalFieldAddressArithmetic);
                if (tornadoAddressArithmeticNode.usages().isEmpty()) {
                    tornadoAddressArithmeticNode.safeDelete();
                }
            }
        }
    }

}
