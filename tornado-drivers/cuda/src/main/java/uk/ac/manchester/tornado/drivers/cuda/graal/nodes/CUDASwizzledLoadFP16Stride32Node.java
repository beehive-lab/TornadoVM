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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
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
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDASwizzledLoadFP16Stride32Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDASwizzledLoadFP16Stride32Node> TYPE =
            NodeClass.create(CUDASwizzledLoadFP16Stride32Node.class);

    @Input private ValueNode fp16LocalArray;
    @Input private ValueNode row;
    @Input private ValueNode column;
    @Input private ValueNode stride;

    public CUDASwizzledLoadFP16Stride32Node(ValueNode fp16LocalArray, ValueNode row,
                                            ValueNode column, ValueNode stride) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.fp16LocalArray = fp16LocalArray;
        this.row = row;
        this.column = column;
        this.stride = stride;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(CUDAKind.HALF));
        tool.append(new CUDALIRStmt.SwizzledLoadFP16Stride32Stmt(
                result,
                generator.operand(fp16LocalArray),
                generator.operand(row),
                generator.operand(column),
                generator.operand(stride)));
        generator.setResult(this, result);
    }
}
