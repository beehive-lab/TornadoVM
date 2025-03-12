/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;

@NodeInfo
public class AddHalfNode extends ValueNode implements ArithmeticLIRLowerable {
    public static final NodeClass<AddHalfNode> TYPE = NodeClass.create(AddHalfNode.class);

    @Input
    private ValueNode x;

    @Input
    private ValueNode y;

    public AddHalfNode(ValueNode x, ValueNode y) {
        super(TYPE, new HalfFloatStamp());
        this.x = x;
        this.y = y;
    }

    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool generator) {
        Value inputX = builder.operand(x);
        Value inputY = builder.operand(y);

        SPIRVArithmeticTool spirvArithmeticTool = (SPIRVArithmeticTool) generator;
        builder.setResult(this, spirvArithmeticTool.emitAdd(inputX, inputY, false));
    }
}
