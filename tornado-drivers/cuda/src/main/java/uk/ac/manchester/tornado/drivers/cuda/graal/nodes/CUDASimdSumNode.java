/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDABinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdSum(float)}.
 *
 * <p>Implements a full-warp reduction using five rounds of butterfly
 * {@code __shfl_xor_sync(0xffffffff, acc, laneMask)} with lane masks 16, 8, 4,
 * 2, 1, each followed by a {@code float} add. The XOR-butterfly leaves the total
 * sum in every lane, matching Metal's {@code simd_sum} semantics (all lanes
 * receive the result), so no separate broadcast step is required.
 *
 * <p>Extends {@link FixedWithNextNode} because warp-shuffle operations are
 * convergent — all lanes must execute them together.
 */
@NodeInfo(shortName = "CUDASimdSum")
public class CUDASimdSumNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDASimdSumNode> TYPE = NodeClass.create(CUDASimdSumNode.class);

    private static final int[] BUTTERFLY_MASKS = { 16, 8, 4, 2, 1 };

    @Input
    private ValueNode value;

    public CUDASimdSumNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        var lirKind = tool.getLIRKind(stamp);
        var intKind = tool.getLIRKind(StampFactory.forKind(JavaKind.Int));

        Value acc = gen.operand(value);

        for (int laneMask : BUTTERFLY_MASKS) {
            // tmp = __shfl_xor_sync(0xffffffff, acc, laneMask)
            Variable tmp = tool.newVariable(lirKind);
            ConstantValue maskConst = new ConstantValue(intKind, JavaConstant.forInt(laneMask));
            tool.append(new CUDALIRStmt.ShuffleSyncStmt(CUDALIRStmt.ShuffleSyncStmt.Mode.XOR, tmp, acc, maskConst));

            // newAcc = acc + tmp
            Variable newAcc = tool.newVariable(lirKind);
            tool.append(new CUDALIRStmt.AssignStmt(newAcc,
                    new CUDABinary.Expr(CUDAAssembler.CUDABinaryOp.ADD, lirKind, acc, tmp)));
            acc = newAcc;
        }

        gen.setResult(this, acc);
    }
}
