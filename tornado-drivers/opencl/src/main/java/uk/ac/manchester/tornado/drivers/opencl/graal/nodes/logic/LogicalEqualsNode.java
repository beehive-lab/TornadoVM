/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes.logic;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp.RELATIONAL_EQ;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBinary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.logic.BinaryLogicalNode;

@NodeInfo(shortName = "==")
public class LogicalEqualsNode extends BinaryLogicalNode {

    public static final NodeClass<LogicalEqualsNode> TYPE = NodeClass.create(LogicalEqualsNode.class);

    public LogicalEqualsNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x, Value y) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new OCLBinary.Expr(RELATIONAL_EQ, tool.getLIRKind(stamp), x, y));
        tool.append(assign);
        return result;
    }
}
