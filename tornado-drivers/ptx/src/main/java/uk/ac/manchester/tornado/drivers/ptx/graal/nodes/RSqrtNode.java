/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(shortName = "PTX-RSQRT")
public class RSqrtNode extends FloatingNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<RSqrtNode> TYPE = NodeClass.create(RSqrtNode.class);

    @Input
    ValueNode value;

    public RSqrtNode(ValueNode x) {
        super(TYPE, StampFactory.forKind(x.getStackKind()));
        this.value = x;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        Value operand = builder.operand(value);
        PTXArithmeticTool oclArithmeticTool = (PTXArithmeticTool) gen;
        builder.setResult(this, oclArithmeticTool.emitRSQRT(operand));
    }

    @Override
    public String getOperation() {
        return "RSQRT";
    }
}
