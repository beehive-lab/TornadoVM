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
package uk.ac.manchester.tornado.drivers.ptx.graal.phases;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFieldAddressArithmeticNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

import java.util.ArrayList;
import java.util.Optional;

/**
 * This phase identifies if a {@code TornadoAddressArithmeticNode} is associated with
 * a {@code PTXDecompressedReadFieldNode}. If this is the case, the {@code TornadoAddressArithmeticNode}
 * is replaced by a {@code PTXFieldAddressArithmeticNode}, which emits the absolute address, as
 * evaluated by the corresponding {@code PTXDecompressedReadFieldNode}.
 */
public class PTXFieldCoopsAccessPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (PTXDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(PTXDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                PTXFieldAddressArithmeticNode ptxFieldAddressArithmetic = new PTXFieldAddressArithmeticNode(readDecompressedField);
                graph.addWithoutUnique(ptxFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(ptxFieldAddressArithmetic);
                if (tornadoAddressArithmeticNode.usages().isEmpty()) {
                    tornadoAddressArithmeticNode.safeDelete();
                }
            }
        }
    }

}
