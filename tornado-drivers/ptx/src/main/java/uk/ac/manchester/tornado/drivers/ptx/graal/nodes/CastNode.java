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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkCastNode;

@NodeInfo
public class CastNode extends FloatingNode implements LIRLowerable, MarkCastNode {

    public static final NodeClass<CastNode> TYPE = NodeClass.create(CastNode.class);

    @Input
    protected ValueNode value;

    protected FloatConvert op;

    public CastNode(Stamp stamp, FloatConvert op, ValueNode value) {
        super(TYPE, stamp);
        this.op = op;
        this.value = value;
    }

    /**
     * Generates the PTX LIR instructions for a cast between two variables. It
     * covers the following cases: - if the result is not a FPU number and the value
     * is a FPU number, then we perform a conversion which rounds towards zero (as
     * Java does). Also, we check if the value is an exceptional case such as NaN,
     * +/- infinity. For this case, we put 0 in the result. - if both operands are
     * FPU, then we do a simple convert operation with the proper rounding modifier
     * (if needed).
     */
    @Override
    public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitCast: convertOp=%s, value=%s", op, value);
        PTXLIRGenerator gen = (PTXLIRGenerator) nodeLIRBuilderTool.getLIRGeneratorTool();
        LIRKind lirKind = gen.getLIRKind(stamp);
        final Variable result = gen.newVariable(lirKind);

        Value value = nodeLIRBuilderTool.operand(this.value);
        PTXKind valueKind = (PTXKind) value.getPlatformKind();
        PTXKind resultKind = (PTXKind) result.getPlatformKind();

        PTXAssembler.PTXUnaryOp opcode;
        if (!resultKind.isFloating() && (valueKind.isFloating() || valueKind.getElementKind().isFloating())) {
            opcode = PTXAssembler.PTXUnaryOp.CVT_INT_RTZ;

            Variable nanPred = gen.newVariable(LIRKind.value(PTXKind.PRED));
            gen.append(new PTXLIRStmt.AssignStmt(nanPred, new PTXUnary.Expr(PTXAssembler.PTXUnaryOp.TESTP_NORMAL, LIRKind.value(valueKind), value)));
            gen.append(new PTXLIRStmt.ConditionalStatement(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(opcode, lirKind, value)), nanPred, false));
            gen.append(new PTXLIRStmt.ConditionalStatement(new PTXLIRStmt.AssignStmt(result, new ConstantValue(LIRKind.value(resultKind), PrimitiveConstant.INT_0)), nanPred, true));
        } else {
            if (resultKind.isF64() && valueKind.isF32()) {
                opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT;
            } else {
                opcode = PTXAssembler.PTXUnaryOp.CVT_FLOAT_RNE;
            }
            gen.append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(opcode, lirKind, value)));
        }

        nodeLIRBuilderTool.setResult(this, result);
    }
}
