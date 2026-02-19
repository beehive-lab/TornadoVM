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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

/**
 * Instruction: OpMemoryBarrier
 */
@NodeInfo
public class SPIRVBarrierNode extends FixedWithNextNode implements LIRLowerable, MemoryKill {

    public static final NodeClass<SPIRVBarrierNode> TYPE = NodeClass.create(SPIRVBarrierNode.class);

    @Override
    public boolean killsInit() {
        return false;
    }

    public enum SPIRVMemFenceFlags {
        GLOBAL(0x200 | 0x10), //
        LOCAL(0x100 | 0x10); //

        private int memorySemantics;

        SPIRVMemFenceFlags(int semantics) {
            this.memorySemantics = semantics;
        }

        public int getMemorySemantics() {
            return memorySemantics;
        }
    }

    private final SPIRVMemFenceFlags BARRIER_TYPE;

    public SPIRVBarrierNode(SPIRVMemFenceFlags flags) {
        super(TYPE, StampFactory.forVoid());
        this.BARRIER_TYPE = flags;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "Append Barrier of type: " + BARRIER_TYPE);
        generator.getLIRGeneratorTool().append(new SPIRVLIRStmt.ExprStmt(new SPIRVUnary.Barrier(BARRIER_TYPE)));
    }
}
