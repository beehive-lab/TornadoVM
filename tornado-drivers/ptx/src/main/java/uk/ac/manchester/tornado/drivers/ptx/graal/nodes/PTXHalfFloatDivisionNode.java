/*
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class PTXHalfFloatDivisionNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<PTXHalfFloatDivisionNode> TYPE = NodeClass.create(PTXHalfFloatDivisionNode.class);

    @Input
    private ValueNode dividend;
    @Input
    private ValueNode divisor;

    public PTXHalfFloatDivisionNode(ValueNode dividend, ValueNode divisor) {
        super(TYPE, new HalfFloatStamp());
        this.dividend = dividend;
        this.divisor = divisor;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Value dividendValue = generator.operand(dividend);
        Value divisorValue = generator.operand(divisor);

        Variable dividendConvertedToFloat = tool.newVariable(LIRKind.value(PTXKind.F32));
        Variable divisorConvertedToFloat = tool.newVariable(LIRKind.value(PTXKind.F32));
        Variable floatResult = tool.newVariable(LIRKind.value(PTXKind.F32));

        Variable halfFloatResult = tool.newVariable(LIRKind.value(PTXKind.F16));

        tool.append(new PTXLIRStmt.DivideHalfFloatStmt(dividendValue, divisorValue, dividendConvertedToFloat, divisorConvertedToFloat, floatResult, halfFloatResult));
        generator.setResult(this, halfFloatResult);
    }
}
