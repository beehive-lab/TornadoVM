/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

/**
 * Loads an m16n8k16 f16 A or B fragment straight from a row-major {@code HalfFloatArray} in
 * global memory ({@code KernelContext.mmaLoadA/mmaLoadB(HalfFloatArray, row, col, ld)}), so a
 * warp needs no shared-memory staging tile and no barrier before {@code mma.sync}.
 */
@NodeInfo
public class CUDAMMALoadGlobalNode extends FixedWithNextNode implements LIRLowerable, MarkMMAFragment, MarkArrayParameterAccess {

    public static final NodeClass<CUDAMMALoadGlobalNode> TYPE = NodeClass.create(CUDAMMALoadGlobalNode.class);

    @Input private ValueNode array;
    @Input private ValueNode row;
    @Input private ValueNode col;
    @Input private ValueNode ld;
    private final boolean isB;
    private final int headerBytes;

    public CUDAMMALoadGlobalNode(ValueNode array, ValueNode row, ValueNode col, ValueNode ld, boolean isB, int headerBytes) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.array = array;
        this.row = row;
        this.col = col;
        this.ld = ld;
        this.isB = isB;
        this.headerBytes = headerBytes;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable frag = tool.newVariable(LIRKind.value(isB ? CUDAKind.MMA_FRAG_B_F16 : CUDAKind.MMA_FRAG_A_F16));
        tool.append(new CUDALIRStmt.MMALoadGlobalStmt(frag, gen.operand(array), gen.operand(row), gen.operand(col), gen.operand(ld), isB, headerBytes));
        gen.setResult(this, frag);
    }

    @Override
    public boolean isAccumulatorFragment() {
        // A/B operand fragments hold packed b32 lanes, not addressable elements.
        return false;
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        return MarkArrayParameterAccess.unwrapPi(parameter) == MarkArrayParameterAccess.unwrapPi(array) ? Access.READ_ONLY : Access.NONE;
    }
}
