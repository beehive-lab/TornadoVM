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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.LocalArrayNode;

/**
 * Metal-specific node for the dp4a (dot-product of 4 bytes) operation.
 * Metal has no native dp4a instruction, so this emits an unrolled 4-element
 * signed-byte dot product plus accumulator.
 */
@NodeInfo
public class MetalDp4aNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<MetalDp4aNode> TYPE = NodeClass.create(MetalDp4aNode.class);

    @Input
    private ValueNode a;

    @Input
    private ValueNode offsetA;

    @Input
    private ValueNode b;

    @Input
    private ValueNode offsetB;

    @Input
    private ValueNode c;

    public MetalDp4aNode(ValueNode a, ValueNode offsetA, ValueNode b, ValueNode offsetB, ValueNode c) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.a = a;
        this.offsetA = offsetA;
        this.b = b;
        this.offsetB = offsetB;
        this.c = c;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(MetalKind.INT));
        Value aVal = generator.operand(a);
        Value offsetAVal = generator.operand(offsetA);
        Value bVal = generator.operand(b);
        Value offsetBVal = generator.operand(offsetB);
        Value cVal = generator.operand(c);
        if (b instanceof LocalArrayNode) {
            // b is in threadgroup memory (no ARRAY_HEADER, no device cast)
            tool.append(new MetalLIRStmt.Dp4aLocalMemStmt(result, aVal, offsetAVal, bVal, offsetBVal, cVal));
        } else {
            tool.append(new MetalLIRStmt.Dp4aStmt(result, aVal, offsetAVal, bVal, offsetBVal, cVal));
        }
        generator.setResult(this, result);
    }
}
