/*
 * Copyright (c) 2018-2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;
import uk.ac.manchester.tornado.runtime.common.exceptions.TornadoUnsupportedError;
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

    private MetalUnaryOp resolveOp() {
        switch (op) {
            case I2D:
            case F2D:
            case L2D:
                return MetalUnaryOp.CAST_TO_DOUBLE;
            case F2I:
            case D2I:
                return MetalUnaryOp.CAST_TO_INT;
            case I2F:
            case D2F:
            case L2F:
                return MetalUnaryOp.CAST_TO_FLOAT;
            case D2L:
            case F2L:
                return MetalUnaryOp.CAST_TO_LONG;
            default:
                TornadoUnsupportedError.unsupported("Conversion unimplemented: ", op.toString());
                break;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /*
         * using as_T reinterprets the data as type T - consider: float x = (float) 1;
         * and int value = 1, float x = &(value);
         */
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        MetalKind oclKind = (MetalKind) lirKind.getPlatformKind();
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);
        if (oclKind.isFloating()) {
            gen.getLIRGeneratorTool().append(new AssignStmt(result, new MetalUnary.Expr(resolveOp(), lirKind, gen.operand(value))));
        } else {
            gen.getLIRGeneratorTool().append(new AssignStmt(result, new MetalUnary.FloatCast(MetalUnaryOp.CAST_TO_INT, lirKind, gen.operand(value))));

        }
        gen.setResult(this, result);
    }
}
