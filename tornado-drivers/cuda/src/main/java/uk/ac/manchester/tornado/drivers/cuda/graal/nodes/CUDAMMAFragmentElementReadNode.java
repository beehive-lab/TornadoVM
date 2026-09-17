/*
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

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * A read of one element of an int32 MMA accumulator fragment, by constant index, into an
 * ordinary {@code int} register: {@code result = frag[index]} in the generated source, where the
 * fragment is the C local array the MMA statements already declare. The fragment value is never
 * treated as a memory address. Only reads, only {@code s32} accumulators, only indices 0..3.
 */
@NodeInfo
public class CUDAMMAFragmentElementReadNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMAFragmentElementReadNode> TYPE = NodeClass.create(CUDAMMAFragmentElementReadNode.class);

    @Input
    private ValueNode fragment;
    private final int index;

    public CUDAMMAFragmentElementReadNode(ValueNode fragment, int index) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.fragment = fragment;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new CUDALIRStmt.MMAFragmentElementReadStmt(result, gen.operand(fragment), index));
        gen.setResult(this, result);
    }
}
