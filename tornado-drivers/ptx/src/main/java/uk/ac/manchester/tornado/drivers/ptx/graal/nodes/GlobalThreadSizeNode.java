/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXBuiltInRegisterArray;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp.MUL_WIDE;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class GlobalThreadSizeNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadSizeNode> TYPE = NodeClass.create(GlobalThreadSizeNode.class);

    @Input
    protected ConstantNode index;

    public GlobalThreadSizeNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitGlobalThreadSize: dim=%s", index);
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(PTXKind.U64));
        PTXNodeLIRBuilder ptxNodeBuilder = (PTXNodeLIRBuilder) gen;

        PTXBuiltInRegisterArray builtIns = new PTXBuiltInRegisterArray(((ConstantValue) gen.operand(index)).getJavaConstant().asInt());
        Variable gridDim = ptxNodeBuilder.getBuiltInAllocation(builtIns.gridDim);
        Variable blockDim = ptxNodeBuilder.getBuiltInAllocation(builtIns.blockDim);

        Value var = tool.append(new PTXLIRStmt.AssignStmt(result, new PTXBinary.Expr(MUL_WIDE, LIRKind.value(PTXKind.U32), gridDim, blockDim))).getResult();

        Variable actualResult = tool.newVariable(LIRKind.value(PTXKind.S32));
        tool.append(new PTXLIRStmt.AssignStmt(actualResult, var));
        gen.setResult(this, actualResult);
    }

}
