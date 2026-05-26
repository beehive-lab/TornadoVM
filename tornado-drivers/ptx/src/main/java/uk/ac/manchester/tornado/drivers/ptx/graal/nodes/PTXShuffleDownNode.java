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
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdShuffleDown(float, int)}.
 *
 * <p>Lowers to the PTX instruction:
 * {@code shfl.sync.down.b32 dest, src, delta, 31, 0xFFFFFFFF;}
 *
 * <p>Extends {@link FixedWithNextNode} (not {@code FloatingNode}) because
 * warp-shuffle operations are convergent — all lanes in the warp must execute
 * them together. Fixing the node prevents the Graal scheduler from hoisting it
 * into a conditional branch where only some lanes would participate.
 */
@NodeInfo(shortName = "PTXShuffleDown")
public class PTXShuffleDownNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PTXShuffleDownNode> TYPE = NodeClass.create(PTXShuffleDownNode.class);

    @Input private ValueNode data;
    @Input private ValueNode delta;

    public PTXShuffleDownNode(ValueNode data, ValueNode delta) {
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
        tool.append(new PTXLIRStmt.ShuffleSyncStmt(
                PTXLIRStmt.ShuffleSyncStmt.Mode.DOWN, result, dataVal, deltaVal));
        gen.setResult(this, result);
    }
}
