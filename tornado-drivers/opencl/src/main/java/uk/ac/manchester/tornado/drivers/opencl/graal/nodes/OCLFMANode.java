/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.FloatingNode;
import tornado.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;

@NodeInfo(shortName = "OCL-FMA")
public class OCLFMANode extends FloatingNode implements ArithmeticLIRLowerable, MarkFloatingPointIntrinsicsNode {

    public static final NodeClass<OCLFMANode> TYPE = NodeClass.create(OCLFMANode.class);

    @Input
    protected ValueNode x;
    @Input
    protected ValueNode y;
    @Input
    protected ValueNode z;

    public OCLFMANode(ValueNode x, ValueNode y, ValueNode z) {
        super(TYPE, StampFactory.forKind(x.getStackKind()));
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        Value op1 = builder.operand(x);
        Value op2 = builder.operand(y);
        Value op3 = builder.operand(z);

        OCLArithmeticTool oclArithmeticTool = (OCLArithmeticTool) gen;
        builder.setResult(this, oclArithmeticTool.emitFMAInstruction(op1, op2, op3));
    }

    @Override
    public String getOperation() {
        return "FMA";
    }
}
