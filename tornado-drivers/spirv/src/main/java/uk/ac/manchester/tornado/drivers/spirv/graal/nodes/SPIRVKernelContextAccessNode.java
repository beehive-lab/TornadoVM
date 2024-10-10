/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

@NodeInfo
public class SPIRVKernelContextAccessNode extends FloatingNode implements LIRLowerable {

    @Input
    private ConstantNode index;

    public static final NodeClass<SPIRVKernelContextAccessNode> TYPE = NodeClass.create(SPIRVKernelContextAccessNode.class);

    public SPIRVKernelContextAccessNode(ConstantNode index) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.index = index;
    }

    public ConstantNode getIndex() {
        return this.index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        // We know we load an integer value
        SPIRVKind spirvKind = SPIRVKind.OP_TYPE_INT_32;
        LIRKind lirKind = LIRKind.value(spirvKind);

        Value value = gen.operand(index);
        SPIRVLIRStmt.ASSIGNIndexedParameter assignStmt = new SPIRVLIRStmt.ASSIGNIndexedParameter(result, new SPIRVUnary.LoadIndexValueFromKernelContext(lirKind, spirvKind, value));
        gen.setResult(this, result);

        SPIRVLIRGenerator spirvlirGenerator = (SPIRVLIRGenerator) gen.getLIRGeneratorTool();
        spirvlirGenerator.append(assignStmt);
    }
}
