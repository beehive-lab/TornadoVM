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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

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
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

/**
 * Reinterprets the bit pattern of an f16 value as a signed short widened to the Java int
 * stack kind, matching what {@code HalfFloat.getHalfFloatValue()} returns.
 *
 * <p>OpenCL C emitted (via OCLLIRStmt.HalfBitsToIntStmt):
 * <pre>{ half h = half_value; result = (int) *((__private short *) &amp;h); }</pre>
 *
 * <p>The pointer access reinterprets the bits as a signed short; widening to int
 * preserves the Java accessor semantics. Explicit {@code & 0xFFFF} still yields unsigned bits.
 */
@NodeInfo
public class OCLConvertHalfBitsToIntNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<OCLConvertHalfBitsToIntNode> TYPE = NodeClass.create(OCLConvertHalfBitsToIntNode.class);

    @Input
    private ValueNode halfValueNode;

    public OCLConvertHalfBitsToIntNode(ValueNode halfValueNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.halfValueNode = halfValueNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(OCLKind.INT));
        Value halfValue = generator.operand(halfValueNode);
        tool.append(new OCLLIRStmt.HalfBitsToIntStmt(result, halfValue));
        generator.setResult(this, result);
    }
}
