/*
 * Copyright (c) 2020, 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFMANode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.ReadHalfFloatNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorElementOpNode;

public class PTXFMAPhase extends Phase {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        graph.getNodes().filter(AddNode.class).forEach(addNode -> {
            NodeIterable<MulNode> inputMuls = addNode.inputs().filter(MulNode.class);
            if (inputMuls.count() > 0) {
                MulNode mul = inputMuls.first();

                ValueNode x = mul.getX();
                ValueNode y = mul.getY();
                ValueNode z = (ValueNode) addNode.inputs().filter(node -> !node.equals(mul)).first();

                // do not apply this optimization for half floats
                if (x instanceof ReadHalfFloatNode || y instanceof ReadHalfFloatNode || z instanceof ReadHalfFloatNode) {
                    return;
                }

                if (x instanceof VectorElementOpNode || y instanceof VectorElementOpNode || z instanceof VectorElementOpNode) {
                    return;
                }

                PTXFMANode newNode = new PTXFMANode(x, y, z);
                graph.addWithoutUnique(newNode);

                mul.removeUsage(addNode);
                if (mul.hasNoUsages())
                    mul.safeDelete();
                addNode.replaceAtUsages(newNode);
                addNode.safeDelete();
            }
        });
    }
}
