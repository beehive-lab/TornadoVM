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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes.logic;

import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalUnaryOp.LOGICAL_NOT;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicNode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.logic.UnaryLogicalNode;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalUnary;

@NodeInfo(shortName = "!")
public class LogicalNotNode extends UnaryLogicalNode {

    public static final NodeClass<LogicalNotNode> TYPE = NodeClass.create(LogicalNotNode.class);

    public LogicalNotNode(LogicNode value) {
        super(TYPE, value);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new MetalUnary.Expr(LOGICAL_NOT, tool.getLIRKind(stamp), x));
        tool.append(assign);
        return result;
    }

}
