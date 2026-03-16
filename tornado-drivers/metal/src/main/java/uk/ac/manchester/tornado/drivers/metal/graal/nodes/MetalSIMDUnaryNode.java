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
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryIntrinsic;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;

/**
 * Graal node for Metal SIMD-group unary intrinsics (MSL §6.9.2):
 * <ul>
 *   <li>{@code simd_sum(x)} — sum of a value across all active SIMD lanes</li>
 *   <li>{@code simd_broadcast_first(x)} — broadcast lane 0's value to all lanes</li>
 * </ul>
 * Each function takes one float argument and returns a float.
 *
 * <p><b>Important:</b> SIMD-group operations are <em>convergent</em> — all threads
 * in the group must execute them together. This node extends {@link FixedWithNextNode}
 * so that the Graal scheduler cannot hoist it into a conditional branch where only
 * some lanes would execute it (which would cause the reduction to see fewer values).
 */
@NodeInfo
public class MetalSIMDUnaryNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MetalSIMDUnaryNode> TYPE = NodeClass.create(MetalSIMDUnaryNode.class);

    public enum Operation {
        SIMD_SUM(MetalUnaryIntrinsic.SIMD_SUM),
        SIMD_BROADCAST_FIRST(MetalUnaryIntrinsic.SIMD_BROADCAST_FIRST);

        private final MetalUnaryIntrinsic intrinsic;

        Operation(MetalUnaryIntrinsic intrinsic) {
            this.intrinsic = intrinsic;
        }

        public MetalUnaryIntrinsic getIntrinsic() {
            return intrinsic;
        }
    }

    @Input
    private ValueNode value;

    private final Operation operation;

    public MetalSIMDUnaryNode(ValueNode value, Operation operation) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.value = value;
        this.operation = operation;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value input = gen.operand(value);
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new MetalLIRStmt.AssignStmt(result,
                new MetalUnary.Intrinsic(operation.getIntrinsic(), tool.getLIRKind(stamp), input)));
        gen.setResult(this, result);
    }
}
