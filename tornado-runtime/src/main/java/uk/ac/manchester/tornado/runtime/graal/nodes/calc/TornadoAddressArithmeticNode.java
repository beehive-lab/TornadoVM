/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.nodes.calc;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo
public class TornadoAddressArithmeticNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<TornadoAddressArithmeticNode> TYPE = NodeClass.create(TornadoAddressArithmeticNode.class);

    @Node.Input
    protected ValueNode base;

    @Node.Input
    protected ValueNode offset;

    public TornadoAddressArithmeticNode(ValueNode base, ValueNode offset) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.base = base;
        this.offset = offset;
    }

    public ValueNode getBase() {
        return base;
    }
    public ValueNode getOffset() {
        return offset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value base = gen.operand(this.base);
        Value offset = gen.operand(this.offset);
        gen.setResult(this, tool.getArithmetic().emitAdd(base, offset, false));
    }
}
