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
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Block-wide reduction of a per-thread value delegated to CUB: a two-stage
 * {@code cub::WarpReduce} composition (per-warp reduce, lane-0 partials to
 * shared memory, first warp reduces the partials). The warp width (32) is a
 * compile-time constant by nature, so any runtime block size is supported via
 * the {@code valid_items} overloads. The result is valid on local thread 0.
 *
 * <p>Extends {@link FixedWithNextNode} because the sequence contains
 * block-level synchronization — all threads must execute it together.
 */
@NodeInfo(shortName = "CUDACubReduce")
public class CUDACubReduceNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDACubReduceNode> TYPE = NodeClass.create(CUDACubReduceNode.class);

    public enum Op {
        ADD, MAX, MIN
    }

    @Input
    private ValueNode value;

    private final Op op;
    private final JavaKind elementKind;

    public CUDACubReduceNode(ValueNode value, Op op, JavaKind elementKind) {
        super(TYPE, StampFactory.forKind(elementKind));
        this.value = value;
        this.op = op;
        this.elementKind = elementKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new CUDALIRStmt.CubWarpReduceStmt(result, gen.operand(value), op, elementKind));
        gen.setResult(this, result);
    }
}
