/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

@NodeInfo(shortName = "VectorSUB(-)")
public class VectorSubNode extends BinaryNode implements LIRLowerable, VectorOp {

    public static final NodeClass<VectorSubNode> TYPE = NodeClass.create(VectorSubNode.class);

    public VectorSubNode(SPIRVKind kind, ValueNode x, ValueNode y) {
        super(TYPE, SPIRVStampFactory.getStampFor(kind), x, y);
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return (stampX instanceof OCLStamp) ? stampX.join(stampY) : stampY.join(stampX);
    }

    @Override
    public Node canonical(CanonicalizerTool ct, ValueNode t, ValueNode t1) {
        return this;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool ct) {
        return this;
    }

    private SPIRVLIROp genBinaryExpr(Variable result, SPIRVAssembler.SPIRVBinaryOp op, LIRKind lirKind, Value x, Value y) {
        return new SPIRVBinary.Expr(result, op, lirKind, x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);

        final Value input1 = gen.operand(x);
        final Value input2 = gen.operand(y);
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitVectorSub: %s - %s", input1, input2);

        SPIRVKind kind = (SPIRVKind) lirKind.getPlatformKind();
        SPIRVAssembler.SPIRVBinaryOp binaryOp = SPIRVAssembler.SPIRVBinaryOp.SUB_INTEGER;

        if (kind.getElementKind().isFloatingPoint() || kind.isHalf()) {
            binaryOp = SPIRVAssembler.SPIRVBinaryOp.SUB_FLOAT;
        }

        gen.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, genBinaryExpr(result, binaryOp, lirKind, input1, input2)));
        gen.setResult(this, result);
    }
}
