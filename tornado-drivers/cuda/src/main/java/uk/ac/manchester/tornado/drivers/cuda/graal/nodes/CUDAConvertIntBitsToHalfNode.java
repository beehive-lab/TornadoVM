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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Reinterprets the low 16 bits of an integer as the bit pattern of an f16 value - the
 * inverse of {@link CUDAConvertHalfBitsToIntNode}, and what {@code new HalfFloat(short)}
 * means: that constructor stores the short as the half's raw bits, it does not convert a
 * number to half precision.
 *
 * <p>CUDA C emitted (via CUDALIRStmt.IntBitsToHalfStmt):
 * <pre>half_value = __ushort_as_half((unsigned short) bits);</pre>
 *
 * <p>The cast to {@code unsigned short} is what drops the sign extension a {@code short}
 * picks up on the Java operand stack, so a negative half (any value with the sign bit set,
 * e.g. {@code 0xC800} for -8.0) keeps its bit pattern.
 */
@NodeInfo
public class CUDAConvertIntBitsToHalfNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<CUDAConvertIntBitsToHalfNode> TYPE = NodeClass.create(CUDAConvertIntBitsToHalfNode.class);

    @Input
    private ValueNode bitsNode;

    public CUDAConvertIntBitsToHalfNode(ValueNode bitsNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.bitsNode = bitsNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable halfValue = tool.newVariable(LIRKind.value(CUDAKind.HALF));
        Value bits = generator.operand(bitsNode);
        tool.append(new CUDALIRStmt.IntBitsToHalfStmt(halfValue, bits));
        generator.setResult(this, halfValue);
    }
}
