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

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.ConstantValue;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdBroadcastFirst(float)}.
 *
 * <p>Lowered to {@code __shfl_sync(0xffffffff, val, 0)}: every lane receives the
 * value held by lane 0 of the warp.
 *
 * <p>Extends {@link FixedWithNextNode} because warp-shuffle operations are
 * convergent — all lanes must execute them together.
 */
@NodeInfo(shortName = "CUDASimdBroadcastFirst")
public class CUDASimdBroadcastFirstNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDASimdBroadcastFirstNode> TYPE = NodeClass.create(CUDASimdBroadcastFirstNode.class);

    @Input
    private ValueNode value;

    public CUDASimdBroadcastFirstNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        var lirKind = tool.getLIRKind(stamp);
        var intKind = tool.getLIRKind(StampFactory.forKind(JavaKind.Int));
        Value source = gen.operand(value);
        Variable result = tool.newVariable(lirKind);
        ConstantValue laneZero = new ConstantValue(intKind, JavaConstant.forInt(0));
        tool.append(new CUDALIRStmt.ShuffleSyncStmt(CUDALIRStmt.ShuffleSyncStmt.Mode.IDX, result, source, laneZero));
        gen.setResult(this, result);
    }
}
