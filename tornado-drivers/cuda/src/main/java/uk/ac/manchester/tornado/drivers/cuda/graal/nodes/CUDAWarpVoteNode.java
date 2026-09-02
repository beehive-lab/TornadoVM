/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
import tornado.graal.compiler.graph.Node.Input;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Graal IR node for the {@code KernelContext} warp-vote intrinsics
 * {@code simdAny(boolean)}, {@code simdAll(boolean)} and {@code simdBallot(boolean)}.
 *
 * <p>Lowered to {@code __any_sync} / {@code __all_sync} / {@code __ballot_sync} over the full member
 * mask. {@code ANY} and {@code ALL} produce a boolean, {@code BALLOT} a lane mask.
 *
 * <p>Extends {@link FixedWithNextNode} because warp votes are convergent — every lane of the warp has
 * to execute the same vote, so the node must not be reordered or duplicated across control flow.
 */
@NodeInfo(shortName = "CUDAWarpVote")
public class CUDAWarpVoteNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAWarpVoteNode> TYPE = NodeClass.create(CUDAWarpVoteNode.class);

    private final CUDALIRStmt.WarpVoteStmt.Mode mode;

    @Input
    private ValueNode predicate;

    public CUDAWarpVoteNode(CUDALIRStmt.WarpVoteStmt.Mode mode, ValueNode predicate) {
        super(TYPE, StampFactory.forKind(mode == CUDALIRStmt.WarpVoteStmt.Mode.BALLOT ? JavaKind.Int : JavaKind.Boolean));
        this.mode = mode;
        this.predicate = predicate;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value source = gen.operand(predicate);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new CUDALIRStmt.WarpVoteStmt(mode, result, source));
        gen.setResult(this, result);
    }
}
