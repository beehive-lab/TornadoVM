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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

/**
 * Graal node for {@link uk.ac.manchester.tornado.api.KernelContext#matrixMultiplyTiled}.
 *
 * <p>Lowers to a self-contained threadgroup-tiled GEMM built on Apple's
 * {@code simdgroup_float8x8} hardware matrix units. Each threadgroup stages a 32x8
 * block of A and an 8x32 block of B in {@code threadgroup} memory and its four SIMD
 * groups cooperatively reuse them to accumulate a 32x32 output tile, contracting over
 * the whole K dimension with the accumulator fragments kept in registers.
 *
 * <p>The whole emitted block must live at the kernel's top scope (the cooperative
 * {@code threadgroup} arrays and barriers require every thread of the threadgroup to
 * participate), so this extends {@link FixedWithNextNode} to keep the Graal scheduler
 * from sinking it into a conditional branch.
 */
@NodeInfo
public class MetalSimdgroupTiledMatmulNode extends FixedWithNextNode implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<MetalSimdgroupTiledMatmulNode> TYPE = NodeClass.create(MetalSimdgroupTiledMatmulNode.class);

    @Input
    private ValueNode a;
    @Input
    private ValueNode b;
    @Input
    private ValueNode c;
    @Input
    private ValueNode m;
    @Input
    private ValueNode n;
    @Input
    private ValueNode k;

    public MetalSimdgroupTiledMatmulNode(ValueNode a, ValueNode b, ValueNode c, ValueNode m, ValueNode n, ValueNode k) {
        super(TYPE, StampFactory.forVoid());
        this.a = a;
        this.b = b;
        this.c = c;
        this.m = m;
        this.n = n;
        this.k = k;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.append(new MetalLIRStmt.SimdgroupTiledMatmulStmt( //
                gen.operand(a), gen.operand(b), gen.operand(c), //
                gen.operand(m), gen.operand(n), gen.operand(k)));
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        if (parameter == a || parameter == b) {
            return Access.READ_ONLY; // A and B matrices are read
        }
        if (parameter == c) {
            return Access.WRITE_ONLY; // C matrix is fully written (one tile per threadgroup)
        }
        return Access.NONE;
    }
}
