/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

@NodeInfo
public class NewLocalArrayNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<NewLocalArrayNode> TYPE = NodeClass.create(NewLocalArrayNode.class);

    @Input protected ConstantNode size;
    @Input protected FixedArrayNode array;

    public NewLocalArrayNode(ConstantNode size, JavaKind kind, OCLMemoryBase base, OCLKind oclKind, FixedArrayNode array) {
        super(TYPE, StampFactory.forKind(kind));
        this.size = size;
        this.array = array;
        array.setMemoryLocation(base);
    }

    public ConstantNode getSize() {
        return this.size;
    }

    public FixedArrayNode getArray() {
        return this.array;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        // array.generate(gen);

        // final Value lengthValue = gen.operand(size);
        // // System.out.printf("gen operand: %s (%s)\n", lengthValue,
        // // lengthValue.getClass().getName());
        //
        // LIRKind lirKind =
        // LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        // final Variable variable =
        // gen.getLIRGeneratorTool().newVariable(lirKind);
        // final OCLBinary.Expr declaration = new
        // OCLBinary.Expr(OCLBinaryTemplate.NEW_LOCAL_INT_ARRAY, lirKind,
        // variable, lengthValue);
        //
        // final OCLLIRStmt.ExprStmt expr = new
        // OCLLIRStmt.ExprStmt(declaration);
        //
        // // System.out.printf("expr: %s\n", expr);
        // gen.getLIRGeneratorTool().append(expr);
        //
        // gen.setResult(array, variable);

    }
}
