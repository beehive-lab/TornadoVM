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
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDAMMALoadAInt8Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMALoadAInt8Node> TYPE = NodeClass.create(CUDAMMALoadAInt8Node.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;
    @OptionalInput private ValueNode byteOffset;

    public CUDAMMALoadAInt8Node(ValueNode tile, ValueNode wmmaK) { this(tile, wmmaK, null); }
    public CUDAMMALoadAInt8Node(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile; this.wmmaK = wmmaK; this.byteOffset = byteOffset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);
        Variable fragA = tool.newVariable(LIRKind.value(CUDAKind.MMA_FRAG_A_S8));
        Value off = (byteOffset == null) ? null : gen.operand(byteOffset);
        int rowStride = 32; // 16×32 s8 viewed as 16×16 b16
        tool.append(new CUDALIRStmt.LdmatrixStmt(
                CUDALIRStmt.LdmatrixStmt.Variant.X4, fragA, tileVal, rowStride, off));
        gen.setResult(this, fragA);
    }
}
