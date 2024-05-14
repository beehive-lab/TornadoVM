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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

@NodeInfo
public class GetArrayNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GetArrayNode> TYPE = NodeClass.create(GetArrayNode.class);
    private final SPIRVKind spirvKind;

    @Input
    private ValueNode arrayNode;

    private JavaKind elementKind;

    public GetArrayNode(SPIRVKind spirvKind, ValueNode array, JavaKind elementKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(spirvKind));
        this.spirvKind = spirvKind;
        this.arrayNode = array;
        this.elementKind = elementKind;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(SPIRVStampFactory.getStampFor(spirvKind));
    }

    public SPIRVKind getSpirvKind() {
        return spirvKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        if (TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
            tool.append(new SPIRVLIRStmt.PassValuePhi(result, generator.operand(arrayNode)));
        } else {
            tool.append(new SPIRVLIRStmt.AssignStmtWithLoad(result, generator.operand(arrayNode)));
        }
        generator.setResult(this, result);
    }
}
