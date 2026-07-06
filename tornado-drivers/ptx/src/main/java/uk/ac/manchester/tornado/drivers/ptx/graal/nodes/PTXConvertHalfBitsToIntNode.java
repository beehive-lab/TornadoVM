/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Reinterprets the raw bit pattern of an f16 value as an unsigned 32-bit
 * integer (zero-extended).  Used to pack two f16 values into a single int32
 * for ldmatrix-based shared-memory tiles.
 *
 * PTX emitted:
 *   mov.b16 __hbits_tmp, %f16_src;   // bitcast, no value conversion
 *   cvt.u32.u16 %u32_result, __hbits_tmp;
 */
@NodeInfo
public class PTXConvertHalfBitsToIntNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<PTXConvertHalfBitsToIntNode> TYPE =
            NodeClass.create(PTXConvertHalfBitsToIntNode.class);

    @Input
    private ValueNode halfValueNode;

    public PTXConvertHalfBitsToIntNode(ValueNode halfValueNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.halfValueNode = halfValueNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(PTXKind.U32));
        Variable b16Tmp = tool.newVariable(LIRKind.value(PTXKind.B16));
        Value halfValue = generator.operand(halfValueNode);
        tool.append(new PTXLIRStmt.HalfBitsToIntStmt(result, halfValue, b16Tmp));
        generator.setResult(this, result);
    }
}
