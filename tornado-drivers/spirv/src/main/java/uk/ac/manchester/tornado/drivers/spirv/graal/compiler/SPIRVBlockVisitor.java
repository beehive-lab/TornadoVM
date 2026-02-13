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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;

public class SPIRVBlockVisitor implements ControlFlowGraph.RecursiveVisitor<HIRBlock> {

    private final SPIRVCompilationResultBuilder crb;
    private SPIRVAssembler assembler;

    public SPIRVBlockVisitor(SPIRVCompilationResultBuilder resultBuilder) {
        this.crb = resultBuilder;
        this.assembler = resultBuilder.getAssembler();
    }

    @Override
    public HIRBlock enter(HIRBlock b) {
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Entering block: " + b);
        if (!b.isLoopHeader() && b.getPredecessorCount() != 0) {
            // Do not generate a label for the first block. This was already generated in
            // the SPIR-V preamble because we need the declaration of all variables.
            assembler.emitBlockLabelIfNotPresent(b, assembler.getFunctionScope());
        }
        if (!b.isLoopHeader()) {
            String blockName = assembler.composeUniqueLabelName(b.toString());
            assembler.pushScope(assembler.getBlockTable().get(blockName));
        }
        crb.emitBlock(b);
        return null;
    }

    @Override
    public void exit(HIRBlock b, HIRBlock value) {

    }

    public void exit(HIRBlock b) {
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "EXIT BLOCK: " + b);
        assembler.popScope();
    }
}
