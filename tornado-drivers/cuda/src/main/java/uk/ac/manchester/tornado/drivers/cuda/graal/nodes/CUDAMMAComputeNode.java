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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDAMMAComputeNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMAComputeNode> TYPE = NodeClass.create(CUDAMMAComputeNode.class);

    @Input private ValueNode fragA;
    @Input private ValueNode fragB;
    @Input private ValueNode fragC;
    private final MMAShape shape;

    public CUDAMMAComputeNode(ValueNode a, ValueNode b, ValueNode c, MMAShape shape) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.fragA = a; this.fragB = b; this.fragC = c; this.shape = shape;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        CUDAKind accKind = (shape == MMAShape.M16N8K32)
                ? CUDAKind.MMA_FRAG_ACC_S32 : CUDAKind.MMA_FRAG_ACC_F32;
        Variable fragD = tool.newVariable(LIRKind.value(accKind));
        tool.append(new CUDALIRStmt.MMAComputeStmt(
                fragD, gen.operand(fragA), gen.operand(fragB), gen.operand(fragC), shape));
        gen.setResult(this, fragD);
    }
}
