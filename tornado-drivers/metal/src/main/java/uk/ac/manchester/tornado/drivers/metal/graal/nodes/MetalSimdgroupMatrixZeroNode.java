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

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.metal.graal.MetalStampFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

/**
 * Produces a zeroed 8x8 single-precision matrix fragment
 * ({@code make_filled_simdgroup_matrix<float,8,8>(0.0f)}), for
 * {@link uk.ac.manchester.tornado.api.KernelContext#simdgroupMatrixZero}.
 *
 * <p>The fragment is an opaque {@code SIMDGROUP_FLOAT8X8} value held in registers.
 */
@NodeInfo
public class MetalSimdgroupMatrixZeroNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<MetalSimdgroupMatrixZeroNode> TYPE = NodeClass.create(MetalSimdgroupMatrixZeroNode.class);

    public MetalSimdgroupMatrixZeroNode() {
        super(TYPE, MetalStampFactory.getStampFor(MetalKind.SIMDGROUP_FLOAT8X8));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(MetalKind.SIMDGROUP_FLOAT8X8));
        tool.append(new MetalLIRStmt.SimdgroupMatrixZeroStmt(result));
        gen.setResult(this, result);
    }
}
