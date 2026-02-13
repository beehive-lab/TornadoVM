/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
import jdk.vm.ci.meta.RawConstant;
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
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class Dp4aNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<Dp4aNode> TYPE = NodeClass.create(Dp4aNode.class);

    @Input
    private ValueNode a;

    @Input
    private ValueNode b;

    @Input
    private ValueNode c;

    @Input
    private ValueNode offset_a;
    @Input
    private ValueNode offset_b;

    private static long HEADER_SIZE = TornadoNativeArray.ARRAY_HEADER;

    public Dp4aNode(ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b, ValueNode c) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.a = a;
        this.b = b;
        this.c = c;
        this.offset_a = offset_a;
        this.offset_b = offset_b;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        // variable to store the result of -> dp4a.s32.s32 result, a, b, c;
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        if (b instanceof LocalArrayNode) {
            // variables for a (global memory)
            Value offset_a_value = generator.operand(offset_a);
            Variable add_header_offset_a = tool.newVariable(LIRKind.value(PTXKind.U64));
            Value base_address_int8_a = generator.operand(a);
            Variable offseted_address_a = tool.newVariable(LIRKind.value(PTXKind.U64));
            Variable load_four_int8_bytes_a = tool.newVariable(tool.getLIRKind(stamp));

            // variables for b (local memory)
            Value offset_b_value = generator.operand(offset_b);
            Variable load_four_int8_bytes_b = tool.newVariable(tool.getLIRKind(stamp));

            // variable for accumulator
            Value accumulator_c = generator.operand(c);

            Value headerVar = tool.emitConstant(LIRKind.value(PTXKind.U64), new RawConstant(HEADER_SIZE));
            Value localArrayValue = generator.operand(b);

            tool.append(new PTXLIRStmt.Dp4aLocalMemoryStmt(result, base_address_int8_a, load_four_int8_bytes_a, offset_a_value, add_header_offset_a, offseted_address_a, accumulator_c, offset_b_value, load_four_int8_bytes_b, localArrayValue, headerVar));
            generator.setResult(this, result);
        } else {
            // variables to calculate the offsets
            Value offset_a_value = generator.operand(offset_a);
            Variable cnv_offset_a = tool.newVariable(LIRKind.value(PTXKind.U64));
            Variable add_header_offset_a = tool.newVariable(LIRKind.value(PTXKind.U64));

            Value offset_b_value = generator.operand(offset_b);
            Variable cnv_offset_b = tool.newVariable(LIRKind.value(PTXKind.U64));
            Variable add_header_offset_b = tool.newVariable(LIRKind.value(PTXKind.U64));

            // address variables for a
            Value base_address_int8_a = generator.operand(a);
            Variable offseted_address_a = tool.newVariable(LIRKind.value(PTXKind.U64));
            Variable load_four_int8_bytes_a = tool.newVariable(tool.getLIRKind(stamp));

            // address variables for b
            Value base_address_int8_b = generator.operand(b);
            Variable offseted_address_b = tool.newVariable(LIRKind.value(PTXKind.U64));
            Variable load_four_int8_bytes_b = tool.newVariable(tool.getLIRKind(stamp));

            // variable for accumulator
            Value accumulator_c = generator.operand(c);

            Value headerVar = tool.emitConstant(LIRKind.value(PTXKind.U64), new RawConstant(HEADER_SIZE));
            tool.append(new PTXLIRStmt.Dp4aStmt(result, base_address_int8_a, load_four_int8_bytes_a, base_address_int8_b, load_four_int8_bytes_b, accumulator_c, offset_a_value, cnv_offset_a, add_header_offset_a, offset_b_value, cnv_offset_b, add_header_offset_b, offseted_address_a, offseted_address_b, headerVar));
            generator.setResult(this, result);
        }
    }

}
