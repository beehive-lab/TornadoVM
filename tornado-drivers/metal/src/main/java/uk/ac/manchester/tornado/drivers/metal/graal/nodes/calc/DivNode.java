/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Florin Blanaru
 *
 */

package uk.ac.manchester.tornado.drivers.metal.graal.nodes.calc;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;


/*
    This implementation is copied from the Graal compiler 0:22. We need to do this because on later versions of the compiler,
    the DivNode as a child of FloatingNode does not exist any longer.
 */

@NodeInfo(
        shortName = "div_node",
        cycles = NodeCycles.CYCLES_32
)
public class DivNode extends BinaryArithmeticNode<ArithmeticOpTable.BinaryOp.Div> {
    public static final NodeClass<DivNode> TYPE = NodeClass.create(DivNode.class);

    private DivNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getDiv(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y) {
        ArithmeticOpTable.BinaryOp<ArithmeticOpTable.BinaryOp.Div> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getDiv();
        Stamp stamp = op.foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, NodeView.DEFAULT);
        return tryConstantFold != null ? tryConstantFold : new DivNode(x, y);
    }

    @Override
    protected ArithmeticOpTable.BinaryOp<ArithmeticOpTable.BinaryOp.Div> getOp(ArithmeticOpTable table) {
        return table.getDiv();
    }

    @Override
    public void generate(NodeLIRBuilderTool builder) {
        generate(builder, builder.getLIRGeneratorTool().getArithmetic());
    }

    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitDiv(nodeValueMap.operand(this.getX()), nodeValueMap.operand(this.getY()), (LIRFrameState)null));
    }
}
