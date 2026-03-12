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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

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
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBinary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

/**
 * Graal IR node for {@code KernelContext.simdShuffleDown(float, int)}.
 *
 * <p>Lowers to the OpenCL sub-group built-in:
 * {@code sub_group_shuffle_down(val, delta)}
 *
 * <p>Extends {@link FixedWithNextNode} because sub-group operations are
 * convergent — all lanes in the sub-group must execute them together.
 */
@NodeInfo(shortName = "OCLSubGroupShuffleDown")
public class OCLSubGroupShuffleDownNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<OCLSubGroupShuffleDownNode> TYPE = NodeClass.create(OCLSubGroupShuffleDownNode.class);

    @Input private ValueNode value;
    @Input private ValueNode delta;

    public OCLSubGroupShuffleDownNode(ValueNode value, ValueNode delta) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
        this.delta = delta;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value valOperand = gen.operand(value);
        Value deltaOperand = gen.operand(delta);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result,
                new OCLBinary.Intrinsic(OCLBinaryIntrinsic.SUB_GROUP_SHUFFLE_DOWN, tool.getLIRKind(stamp), valOperand, deltaOperand)));
        gen.setResult(this, result);
    }
}
