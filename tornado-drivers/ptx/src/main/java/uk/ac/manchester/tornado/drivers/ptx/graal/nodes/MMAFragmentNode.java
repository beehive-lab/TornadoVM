/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for {@code KernelContext.mmaFragment(float)}.
 *
 * <p>Declares a per-lane C/D accumulator fragment for MMA operations.
 * For shape {@code m16n8k16} this corresponds to 4 × f32 registers per lane
 * ({@code rd0..rd3}).
 *
 * <p>Lowers to {@code mov.f32 <reg_N>, <initValue>;} emitted once per
 * fragment register (4 movs total for m16n8k16).
 *
 * <p>Extends {@link FixedWithNextNode} because the fragment's register scope
 * must remain consistent across the warp-collective {@code mma.sync} instruction
 * that consumes it. Letting Graal float this node could result in the
 * accumulator being re-materialised inside a loop, breaking K-dimension
 * accumulation.
 */
@NodeInfo(shortName = "MMAFragment")
public class MMAFragmentNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMAFragmentNode> TYPE = NodeClass.create(MMAFragmentNode.class);

    @Input private ValueNode initValue;
    private final boolean isInt8;
    /** Number of f32 registers per lane — fixed at 4 for m16n8k16. */
    private static final int FRAGMENT_SIZE = 4;

    public MMAFragmentNode(ValueNode initValue, boolean isInt8) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.initValue = initValue;
        this.isInt8 = isInt8;
    }

    public MMAFragmentNode(ValueNode initValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.initValue = initValue;
        this.isInt8 = false;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value initVal = gen.operand(initValue);
        PTXKind fragKind = isInt8 ? PTXKind.MMA_FRAG_ACC_S32 : PTXKind.MMA_FRAG_ACC_F32;
        Variable fragment = tool.newVariable(LIRKind.value(fragKind));

        String movType = isInt8 ? "mov.s32" : "mov.f32";
        String regType = isInt8 ? ".s32" : ".f32";
        tool.append(new PTXLIRStmt.MMAFragmentStmt(fragment, initVal, FRAGMENT_SIZE, movType, regType));
        gen.setResult(this, fragment);
    }
}
