/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXUnary;

@NodeInfo
public class PTXBarrierNode extends FixedWithNextNode implements LIRLowerable, MemoryNode {

    public static final NodeClass<PTXBarrierNode> TYPE = NodeClass.create(PTXBarrierNode.class);

    private final int ctaInstance;
    private final int numberOfThreads;

    public PTXBarrierNode(int ctaInstance, int numberOfThreads) {
        super(TYPE, StampFactory.forVoid());
        this.ctaInstance = ctaInstance;
        this.numberOfThreads = numberOfThreads;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().append(new PTXLIRStmt.ExprStmt(new PTXUnary.Barrier(PTXAssembler.PTXUnaryIntrinsic.BARRIER_SYNC, ctaInstance, numberOfThreads)));
    }
}
