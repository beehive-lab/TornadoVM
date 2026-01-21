/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkCastNode;

@NodeInfo(shortName = "SPIRVCastNode")
public class CastNode extends FloatingNode implements LIRLowerable, MarkCastNode {

    public static final NodeClass<CastNode> TYPE = NodeClass.create(CastNode.class);

    @Input
    protected ValueNode value;
    protected FloatConvert op;

    public CastNode(Stamp stamp, FloatConvert op, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
        this.op = op;
    }

    private SPIRVUnary.CastOperations resolveOp(Variable result, LIRKind lirKind, Value value) {
        return switch (op) {
            case I2F -> new SPIRVUnary.CastIToFloat(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_32);
            case I2D -> new SPIRVUnary.CastIToFloat(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_64);
            case D2F -> new SPIRVUnary.CastFloatDouble(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_32);
            case F2D -> new SPIRVUnary.CastFloatDouble(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_64);
            case L2D -> new SPIRVUnary.CastFloatDouble(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_64);
            case L2F -> new SPIRVUnary.CastFloatToLong(lirKind, result, value, SPIRVKind.OP_TYPE_FLOAT_32);
            case F2I -> new SPIRVUnary.CastFloatToInt(lirKind, result, value, SPIRVKind.OP_TYPE_INT_32);
            default -> throw new RuntimeException("Conversion Cast Operation unimplemented: " + op);
        };
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SPIRVLIRGenerator gen = (SPIRVLIRGenerator) generator.getLIRGeneratorTool();
        LIRKind lirKind = gen.getLIRKind(stamp);
        final Variable result = gen.newVariable(lirKind);
        Value value = generator.operand(this.value);
        SPIRVUnary.CastOperations cast = resolveOp(result, lirKind, value);
        gen.append(new SPIRVLIRStmt.AssignStmt(result, cast));
        generator.setResult(this, result);
    }
}
