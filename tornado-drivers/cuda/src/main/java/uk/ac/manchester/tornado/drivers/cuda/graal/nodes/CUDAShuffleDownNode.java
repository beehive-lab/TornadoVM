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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdShuffleDown(float, int)}.
 *
 * <p>Lowered to a single CUDA warp-shuffle intrinsic
 * {@code __shfl_down_sync(0xffffffff, val, delta)}: the calling lane receives
 * the value held by the lane {@code delta} positions ahead in the warp.
 *
 * <p>Extends {@link FixedWithNextNode} because warp-shuffle operations are
 * convergent — all lanes must execute them together, so the node must not be
 * floated or reordered.
 */
@NodeInfo(shortName = "CUDAShuffleDown")
public class CUDAShuffleDownNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAShuffleDownNode> TYPE = NodeClass.create(CUDAShuffleDownNode.class);

    @Input
    private ValueNode value;

    @Input
    private ValueNode delta;

    public CUDAShuffleDownNode(ValueNode value, ValueNode delta) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
        this.delta = delta;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value source = gen.operand(value);
        Value operand = gen.operand(delta);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new CUDALIRStmt.ShuffleSyncStmt(CUDALIRStmt.ShuffleSyncStmt.Mode.DOWN, result, source, operand));
        gen.setResult(this, result);
    }
}
