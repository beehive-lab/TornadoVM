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
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBinary;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

/**
 * Graal node for {@code simd_shuffle_down(data, delta)} (MSL §6.9.2).
 *
 * <p>Each thread receives the value held by the thread {@code delta} lanes ahead
 * within the same SIMD group. Useful for warp-shuffle reductions without
 * threadgroup memory.
 *
 * @param data  the float value to shuffle
 * @param delta lane offset (ushort in MSL, int at the Java API level)
 *
 * <p><b>Important:</b> SIMD-group operations are <em>convergent</em> — all threads
 * must execute them together. This node extends {@link FixedWithNextNode} to prevent
 * the scheduler from placing it inside a branch taken by only some lanes.
 */
@NodeInfo
public class MetalSIMDShuffleDownNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MetalSIMDShuffleDownNode> TYPE = NodeClass.create(MetalSIMDShuffleDownNode.class);

    @Input
    private ValueNode data;

    @Input
    private ValueNode delta;

    public MetalSIMDShuffleDownNode(ValueNode data, ValueNode delta) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.data = data;
        this.delta = delta;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value dataVal = gen.operand(data);
        Value deltaVal = gen.operand(delta);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new MetalLIRStmt.AssignStmt(result,
                new MetalBinary.Intrinsic(MetalBinaryIntrinsic.SIMD_SHUFFLE_DOWN, tool.getLIRKind(stamp), dataVal, deltaVal)));
        gen.setResult(this, result);
    }
}
