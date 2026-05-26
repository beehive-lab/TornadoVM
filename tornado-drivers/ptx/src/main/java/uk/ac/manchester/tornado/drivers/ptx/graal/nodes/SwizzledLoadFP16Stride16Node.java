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
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class SwizzledLoadFP16Stride16Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<SwizzledLoadFP16Stride16Node> TYPE = NodeClass.create(SwizzledLoadFP16Stride16Node.class);

    @Input
    private ValueNode fp16_local_array;

    @Input
    private ValueNode row;

    @Input
    private ValueNode column;

    @Input
    private ValueNode stride;

    public SwizzledLoadFP16Stride16Node(ValueNode fp16_local_array, ValueNode row, ValueNode column, ValueNode stride) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.fp16_local_array = fp16_local_array;
        this.row = row;
        this.column = column;
        this.stride = stride;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(PTXKind.F16));

        Value localArray = generator.operand(fp16_local_array);
        Value rowVal    = generator.operand(row);
        Value colVal    = generator.operand(column);
        Value strideVal = generator.operand(stride);

        Variable linIdx  = tool.newVariable(LIRKind.value(PTXKind.S32));
        Variable byteOff = tool.newVariable(LIRKind.value(PTXKind.S32));
        Variable shifted = tool.newVariable(LIRKind.value(PTXKind.S32));
        Variable masked  = tool.newVariable(LIRKind.value(PTXKind.S32));
        Variable xorTerm = tool.newVariable(LIRKind.value(PTXKind.S32));
        Variable swzByte = tool.newVariable(LIRKind.value(PTXKind.S32));

        tool.append(new PTXLIRStmt.SwizzledLoadFP16Stride16Stmt(
                result, localArray, rowVal, colVal, strideVal,
                linIdx, byteOff, shifted, masked, xorTerm, swzByte));
        generator.setResult(this, result);
    }
}
