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

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Calls a cuda_fp16.h packed-half2 intrinsic ({@code __hfma2}, {@code __floats2half2_rn},
 * {@code __low2float}, {@code __high2float}, ...) over its inputs. The result stamp is
 * supplied by the invocation plugin (a packed {@code __half2} or a scalar {@code float}).
 */
@NodeInfo
public class Half2IntrinsicNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<Half2IntrinsicNode> TYPE = NodeClass.create(Half2IntrinsicNode.class);

    @Input
    private NodeInputList<ValueNode> operands;

    private final String function;

    public Half2IntrinsicNode(String function, Stamp stamp, ValueNode... operands) {
        super(TYPE, stamp);
        this.function = function;
        this.operands = new NodeInputList<>(this, operands);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value[] values = new Value[operands.size()];
        for (int i = 0; i < operands.size(); i++) {
            values[i] = generator.operand(operands.get(i));
        }
        tool.append(new CUDALIRStmt.Half2IntrinsicStmt(function, result, values));
        generator.setResult(this, result);
    }
}
