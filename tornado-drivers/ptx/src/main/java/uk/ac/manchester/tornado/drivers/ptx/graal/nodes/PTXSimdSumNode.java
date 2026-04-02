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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

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
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdSum(float)}.
 *
 * <p>Implements a full-warp reduction using five rounds of
 * {@code shfl.sync.down.b32} + {@code add.f32} with deltas 16, 8, 4, 2, 1, followed by a {@code shfl.sync.idx.b32} broadcast from lane 0 so that all lanes receive the final sum (matching Metal's
 * {@code simd_sum} semantics).
 *
 * <p>Extends {@link FixedWithNextNode} because warp-shuffle operations are
 * convergent — all lanes must execute them together.
 */
@NodeInfo(shortName = "PTXSimdSum")
public class PTXSimdSumNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PTXSimdSumNode> TYPE = NodeClass.create(PTXSimdSumNode.class);

    private static final int[] BUTTERFLY_DELTAS = { 16, 8, 4, 2, 1 };

    @Input
    private ValueNode value;

    public PTXSimdSumNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        var lirKind = tool.getLIRKind(stamp);
        var intKind = tool.getLIRKind(StampFactory.forKind(JavaKind.Int));

        Value acc = gen.operand(value);

        for (int delta : BUTTERFLY_DELTAS) {
            // tmp = shfl.sync.down.b32 acc, delta
            Variable tmp = tool.newVariable(lirKind);
            ConstantValue deltaConst = new ConstantValue(intKind, JavaConstant.forInt(delta));
            tool.append(new PTXLIRStmt.ShuffleSyncStmt(PTXLIRStmt.ShuffleSyncStmt.Mode.DOWN, tmp, acc, deltaConst));

            // newAcc = add.f32 acc, tmp
            Variable newAcc = tool.newVariable(lirKind);
            tool.append(new PTXLIRStmt.AssignStmt(newAcc,
                    new PTXBinary.Expr(PTXAssembler.PTXBinaryOp.ADD, lirKind, acc, tmp)));
            acc = newAcc;
        }

        // Broadcast lane 0's final sum to all lanes so every lane sees the
        // same result, matching Metal's simd_sum() semantics.
        Variable broadcast = tool.newVariable(lirKind);
        ConstantValue zero = new ConstantValue(intKind, JavaConstant.forInt(0));
        tool.append(new PTXLIRStmt.ShuffleSyncStmt(PTXLIRStmt.ShuffleSyncStmt.Mode.IDX, broadcast, acc, zero));

        gen.setResult(this, broadcast);
    }
}
