/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.nodes.calc;

/*
    This implementation is copied from the Graal compiler 0:22. We need to do this because on later versions of the compiler,
    the DivNode as a child of FloatingNode does not exist any longer.
 */

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.common.logging.Logger;

@NodeInfo(shortName = "div_node")
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emtiDiv: x=%s, y=%s", x, y);
        nodeValueMap.setResult(this, gen.emitDiv(nodeValueMap.operand(this.getX()), nodeValueMap.operand(this.getY()), null));
    }
}
