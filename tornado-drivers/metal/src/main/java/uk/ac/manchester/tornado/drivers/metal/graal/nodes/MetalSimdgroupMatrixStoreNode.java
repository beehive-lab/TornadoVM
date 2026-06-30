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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

/**
 * Stores an 8x8 fragment to device memory ({@code simdgroup_store}), for
 * {@link uk.ac.manchester.tornado.api.KernelContext#simdgroupMatrixStore}.
 *
 * <p>A pure side-effect that must run convergently across the SIMD group, so it is
 * fixed in the control flow. Implements {@link MarkArrayParameterAccess} so the
 * dataflow analysis marks the destination array as written — otherwise the
 * device-to-host copy is skipped and results come back as zeros.
 */
@NodeInfo
public class MetalSimdgroupMatrixStoreNode extends FixedWithNextNode implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<MetalSimdgroupMatrixStoreNode> TYPE = NodeClass.create(MetalSimdgroupMatrixStoreNode.class);

    @Input
    private ValueNode matrix;
    @Input
    private ValueNode array;
    @Input
    private ValueNode base;
    @Input
    private ValueNode stride;

    public MetalSimdgroupMatrixStoreNode(ValueNode matrix, ValueNode array, ValueNode base, ValueNode stride) {
        super(TYPE, StampFactory.forVoid());
        this.matrix = matrix;
        this.array = array;
        this.base = base;
        this.stride = stride;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.append(new MetalLIRStmt.SimdgroupMatrixStoreStmt(gen.operand(matrix), gen.operand(array), gen.operand(base), gen.operand(stride)));
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        return (parameter == MarkArrayParameterAccess.unwrapPi(array)) ? Access.WRITE_ONLY : Access.NONE;
    }
}
