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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertSToF;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFConvert;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

@NodeInfo
public class SPIRVConvertHalfToFloat extends ValueNode implements LIRLowerable {

    public static final NodeClass<SPIRVConvertHalfToFloat> TYPE = NodeClass.create(SPIRVConvertHalfToFloat.class);

    @Input
    private ValueNode halfValueNode;

    public SPIRVConvertHalfToFloat(ValueNode halfValueNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.halfValueNode = halfValueNode;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable floatValue = tool.newVariable(LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_32));
        Value halfValue = generator.operand(halfValueNode);
        LIRKind lirKind = LIRKind.value(SPIRVKind.OP_TYPE_FLOAT_32);
        SPIRVUnary.CastOperations cast = new SPIRVUnary.CastFloatDouble(lirKind, floatValue, halfValue, SPIRVKind.OP_TYPE_FLOAT_32);
        tool.append(new SPIRVLIRStmt.AssignStmt(floatValue, cast));
        generator.setResult(this, floatValue);
    }

}
