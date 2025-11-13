/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFieldAddressArithmeticNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

import java.util.ArrayList;
import java.util.Optional;

/**
 * This phase identifies if a {@code TornadoAddressArithmeticNode} is associated with
 * a {@code OCLDecompressedReadFieldNode}. If this is the case, the {@code TornadoAddressArithmeticNode}
 * is replaced by a {@code OCLFieldAddressArithmeticNode}, which emits the absolute address, as
 * evaluated by the corresponding {@code OCLDecompressedReadFieldNode}.
 */
public class OCLFieldCoopsAccessPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        ArrayList<TornadoAddressArithmeticNode> toBeDeleted = new ArrayList<>();
        for (OCLDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(OCLDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                OCLFieldAddressArithmeticNode oclFieldAddressArithmetic = new OCLFieldAddressArithmeticNode(readDecompressedField);
                graph.addWithoutUnique(oclFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(oclFieldAddressArithmetic);
                toBeDeleted.add(tornadoAddressArithmeticNode);
            }
        }
        for (int i = 0; i < toBeDeleted.size(); i++) {
            toBeDeleted.get(i).safeDelete();
        }
    }

}
