/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkVectorStore;

/**
 * The {@code VectorStoreGlobalMemory} represents a vector-write to global
 * memory.
 */
@NodeInfo(nameTemplate = "VectorStoreGlobalMemory")
public final class VectorStoreGlobalMemory extends FixedWithNextNode implements LIRLowerable, MarkVectorStore {

    public static final NodeClass<VectorStoreGlobalMemory> TYPE = NodeClass.create(VectorStoreGlobalMemory.class);

    @Input
    ValueNode value;
    @Input
    ValueNode address;

    public VectorStoreGlobalMemory(PTXKind vectorKind, ValueNode address, ValueNode value) {
        super(TYPE, PTXStampFactory.getStampFor(vectorKind));
        this.value = value;
        this.address = address;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value), null, TornadoMemoryOrder.GPU_MEMORY_MODE);
    }
}
