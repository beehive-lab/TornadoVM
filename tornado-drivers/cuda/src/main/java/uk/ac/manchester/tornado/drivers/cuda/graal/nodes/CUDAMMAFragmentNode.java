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
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class CUDAMMAFragmentNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMAFragmentNode> TYPE = NodeClass.create(CUDAMMAFragmentNode.class);

    @Input private ValueNode initValue;
    private final boolean isInt8;
    private static final int FRAGMENT_SIZE = 4;

    public CUDAMMAFragmentNode(ValueNode initValue) {
        this(initValue, false);
    }

    public CUDAMMAFragmentNode(ValueNode initValue, boolean i8)  {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.initValue = initValue;
        this.isInt8 = i8;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value initVal = gen.operand(initValue);
        CUDAKind fragKind = isInt8 ? CUDAKind.MMA_FRAG_ACC_S32 : CUDAKind.MMA_FRAG_ACC_F32;
        Variable fragment = tool.newVariable(LIRKind.value(fragKind));
        tool.append(new CUDALIRStmt.MMAFragmentStmt(fragment, initVal, FRAGMENT_SIZE, isInt8));
        gen.setResult(this, fragment);
    }
}
