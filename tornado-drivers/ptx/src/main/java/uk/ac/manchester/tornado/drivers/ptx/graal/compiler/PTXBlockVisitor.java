/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;

public class PTXBlockVisitor implements ControlFlowGraph.RecursiveVisitor<HIRBlock> {
    private final PTXCompilationResultBuilder crb;
    private PTXAssembler asm;

    public PTXBlockVisitor(PTXCompilationResultBuilder resultBuilder, PTXAssembler asm) {
        this.crb = resultBuilder;
        this.asm = asm;
    }

    @Override
    public HIRBlock enter(HIRBlock block) {
        asm.eol();
        asm.emitBlockLabel(block);
        crb.emitBlock(block);
        return null;
    }

    @Override
    public void exit(HIRBlock b, HIRBlock value) {
    }
}
