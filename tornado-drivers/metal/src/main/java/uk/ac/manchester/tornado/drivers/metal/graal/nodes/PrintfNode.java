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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.ExprStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalPrintf;

@NodeInfo(shortName = "printf")
public class PrintfNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PrintfNode> TYPE = NodeClass.create(PrintfNode.class);

    @Input
    private NodeInputList<ValueNode> inputs;

    public PrintfNode(ValueNode... values) {
        super(TYPE, StampFactory.forVoid());
        this.inputs = new NodeInputList<>(this, values);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] args = new Value[inputs.size()];
        for (int i = 0; i < args.length; i++) {

            ValueNode param = inputs.get(i);
            if (param.isConstant()) {
                args[i] = gen.operand(param);
            } else {
                args[i] = gen.getLIRGeneratorTool().emitMove(gen.operand(param));
            }
        }
        gen.getLIRGeneratorTool().append(new ExprStmt(new MetalPrintf(args)));
    }

}
