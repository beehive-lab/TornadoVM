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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalStampFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

/**
 * Loads an 8x8 single-precision fragment from {@code array} starting at element
 * {@code base} with {@code stride} elements between rows ({@code simdgroup_load}),
 * for {@link uk.ac.manchester.tornado.api.KernelContext#simdgroupMatrixLoad}.
 *
 * <p>The source may be a device {@code FloatArray} or a threadgroup {@code float[]}
 * (when {@code array} is a {@link LocalArrayNode}). Implements
 * {@link MarkArrayParameterAccess} so the dataflow analysis sees the array as read
 * (an opaque intrinsic consuming a kernel parameter is otherwise invisible to it).
 *
 * <p>Fixed (not floating): a threadgroup load must stay ordered after the cooperative
 * stores and {@code threadgroup_barrier} that populate the staging buffer, so the
 * scheduler must not be free to sink or hoist it.
 */
@NodeInfo
public class MetalSimdgroupMatrixLoadNode extends FixedWithNextNode implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<MetalSimdgroupMatrixLoadNode> TYPE = NodeClass.create(MetalSimdgroupMatrixLoadNode.class);

    @Input
    private ValueNode array;
    @Input
    private ValueNode base;
    @Input
    private ValueNode stride;

    public MetalSimdgroupMatrixLoadNode(ValueNode array, ValueNode base, ValueNode stride) {
        super(TYPE, MetalStampFactory.getStampFor(MetalKind.SIMDGROUP_FLOAT8X8));
        this.array = array;
        this.base = base;
        this.stride = stride;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(MetalKind.SIMDGROUP_FLOAT8X8));
        boolean local = array instanceof LocalArrayNode;
        tool.append(new MetalLIRStmt.SimdgroupMatrixLoadStmt(result, gen.operand(array), gen.operand(base), gen.operand(stride), local));
        gen.setResult(this, result);
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        return (parameter == MarkArrayParameterAccess.unwrapPi(array)) ? Access.READ_ONLY : Access.NONE;
    }
}
