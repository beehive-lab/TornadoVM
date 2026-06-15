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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.metal.graal.MetalStampFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

/**
 * Computes {@code a * b + c} for three 8x8 fragments
 * ({@code simdgroup_multiply_accumulate}), producing a new fragment, for
 * {@link uk.ac.manchester.tornado.api.KernelContext#simdgroupMatrixMultiplyAccumulate}.
 *
 * <p>Fixed (not floating): it must stay ordered between the fragment loads it consumes
 * and the {@code threadgroup_barrier} that follows, so the scheduler cannot move it.
 */
@NodeInfo
public class MetalSimdgroupMatrixMmaNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MetalSimdgroupMatrixMmaNode> TYPE = NodeClass.create(MetalSimdgroupMatrixMmaNode.class);

    @Input
    private ValueNode a;
    @Input
    private ValueNode b;
    @Input
    private ValueNode c;

    public MetalSimdgroupMatrixMmaNode(ValueNode a, ValueNode b, ValueNode c) {
        super(TYPE, MetalStampFactory.getStampFor(MetalKind.SIMDGROUP_FLOAT8X8));
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(MetalKind.SIMDGROUP_FLOAT8X8));
        tool.append(new MetalLIRStmt.SimdgroupMatrixMmaStmt(result, gen.operand(a), gen.operand(b), gen.operand(c)));
        gen.setResult(this, result);
    }
}
