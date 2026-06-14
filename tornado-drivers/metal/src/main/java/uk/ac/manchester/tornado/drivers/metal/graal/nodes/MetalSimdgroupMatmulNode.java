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
import org.graalvm.compiler.graph.NodeInputList;
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
 * Graal node for {@link uk.ac.manchester.tornado.api.KernelContext#matrixMultiply8x8}.
 *
 * <p>Lowers to Apple's {@code simdgroup_float8x8} hardware matrix-multiply instructions
 * ({@code simdgroup_load} / {@code simdgroup_multiply_accumulate} / {@code simdgroup_store}),
 * cooperatively computing one 8x8 output tile across the 32-lane SIMD group.
 *
 * <p>SIMD-group matrix ops are <em>convergent</em> (all lanes must participate), so this
 * extends {@link FixedWithNextNode} to keep the Graal scheduler from sinking it into a
 * conditional branch where only some lanes would execute.
 */
@NodeInfo
public class MetalSimdgroupMatmulNode extends FixedWithNextNode implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<MetalSimdgroupMatmulNode> TYPE = NodeClass.create(MetalSimdgroupMatmulNode.class);

    // Many operands (10) exceed Graal's inline edge limit, so they are stored in a
    // NodeInputList. Order: a, aBase, lda, b, bBase, ldb, c, cBase, ldc, k.
    @Input
    private NodeInputList<ValueNode> args;

    public MetalSimdgroupMatmulNode(ValueNode a, ValueNode aBase, ValueNode lda, ValueNode b, ValueNode bBase, ValueNode ldb, ValueNode c, ValueNode cBase, ValueNode ldc, ValueNode k) {
        super(TYPE, StampFactory.forVoid());
        this.args = new NodeInputList<>(this, new ValueNode[] { a, aBase, lda, b, bBase, ldb, c, cBase, ldc, k });
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.append(new MetalLIRStmt.SimdgroupMatmulStmt( //
                gen.operand(args.get(0)), gen.operand(args.get(1)), gen.operand(args.get(2)), //
                gen.operand(args.get(3)), gen.operand(args.get(4)), gen.operand(args.get(5)), //
                gen.operand(args.get(6)), gen.operand(args.get(7)), gen.operand(args.get(8)), //
                gen.operand(args.get(9))));
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        if (parameter == args.get(0) || parameter == args.get(3)) {
            return Access.READ_ONLY; // A and B matrices are read
        }
        if (parameter == args.get(6)) {
            return Access.WRITE_ONLY; // C matrix is written (overwritten per tile)
        }
        return Access.NONE;
    }
}
