/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "AllocateLocalMemory")
public class AllocateLocalMemoryNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<AllocateLocalMemoryNode> TYPE = NodeClass.create(AllocateLocalMemoryNode.class);

    @Input
    ValueNode size;

    public AllocateLocalMemoryNode(ValueNode size, Stamp stamp) {
        super(TYPE, stamp);
        this.size = size;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        //
        // final Value lengthValue = gen.operand(size);
        //
        // LIRKind lirKind =
        // LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        // final Variable variable = tool.newVariable(lirKind);
        // final OCLBinary.Expr declaration = new
        // OCLBinary.Expr(OCLKind.resolveTemplateType(elementType), lirKind, variable,
        // lengthValue);
        //
        // final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);
        // tool.append(expr);
        // gen.setResult(this, variable);
    }
}
