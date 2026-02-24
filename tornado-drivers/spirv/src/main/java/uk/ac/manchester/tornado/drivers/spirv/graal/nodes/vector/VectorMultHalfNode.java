/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

@NodeInfo
public class VectorMultHalfNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<VectorMultHalfNode> TYPE = NodeClass.create(VectorMultHalfNode.class);

    @Input
    private ValueNode x;

    @Input
    private ValueNode y;

    public VectorMultHalfNode(ValueNode x, ValueNode y) {
        super(TYPE, new HalfFloatStamp());
        this.x = x;
        this.y = y;
    }

    private SPIRVLIROp genBinaryExpr(Variable result, SPIRVAssembler.SPIRVBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new SPIRVBinary.Expr(result, op, lirKind, x, y);
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRKind lirKind = generator.getLIRGeneratorTool().getLIRKind(stamp);
        final Variable result = generator.getLIRGeneratorTool().newVariable(lirKind);

        final Value input1 = generator.operand(x);
        final Value input2 = generator.operand(y);

        SPIRVAssembler.SPIRVBinaryOp binaryOp = SPIRVAssembler.SPIRVBinaryOp.MULT_FLOAT;

        generator.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, genBinaryExpr(result, binaryOp, lirKind, input1, input2)));
        generator.setResult(this, result);
    }

}
