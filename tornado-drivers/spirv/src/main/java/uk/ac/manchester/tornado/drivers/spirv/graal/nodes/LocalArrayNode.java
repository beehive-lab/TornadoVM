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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkLocalArray;

/**
 * Generates the LIR for declaring a SPIR-V array in local memory.
 */
@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray, MemoryKill {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ConstantNode length;

    protected SPIRVArchitecture.SPIRVMemoryBase memoryBase;
    protected SPIRVKind elementKind;

    public LocalArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elementKind = SPIRVKind.fromJavaKind(elementType.getJavaKind());
    }

    public LocalArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, JavaKind elementType, ConstantNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elementKind = SPIRVKind.fromJavaKind(elementType);
    }

    public SPIRVArchitecture.SPIRVMemoryBase getMemoryRegister() {
        return memoryBase;
    }

    public ConstantNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        final Value lengthValue = generator.operand(length);
        LIRKind lirKind = LIRKind.value(elementKind);

        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        final AllocatableValue resultArray = tool.newVariable(lirKind);

        final SPIRVBinary.LocalArrayAllocation localArray = new SPIRVBinary.LocalArrayAllocation(lirKind, resultArray, lengthValue);
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "Local Array Allocation: " + resultArray + " with type: " + lirKind);

        generator.setResult(this, resultArray);
        tool.append(new SPIRVLIRStmt.LocalArrayAllocation(localArray));

    }
}
