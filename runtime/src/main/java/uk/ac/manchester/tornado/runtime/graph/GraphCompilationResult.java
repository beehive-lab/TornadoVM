/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
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
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import uk.ac.manchester.tornado.runtime.graph.nodes.*;

public class GraphCompilationResult {

    public static final int MAX_TVM_BYTECODE_SIZE = Integer.parseInt(getProperty("tornado.tvm.maxbytecodesize", "1024"));

    private byte[] code;
    private TornadoGraphAssembler asm;
    private int gtid;

    public GraphCompilationResult() {
        code = new byte[MAX_TVM_BYTECODE_SIZE];
        asm = new TornadoGraphAssembler(code);
        gtid = 0;
    }

    public void begin(int numContexts, int numStacks, int numDeps) {
        asm.setup(numContexts, numStacks, numDeps);
        for (int i = 0; i < numContexts; i++) {
            asm.context(i);
        }
        asm.begin();
    }

    public void barrier(int dep) {
        asm.barrier(dep);
    }

    public void end() {
        asm.end();
    }

    public void emitAsyncNode(TornadoGraph graph, ExecutionContext context, AbstractNode node, int ctx, int depIn) {

        // System.out.printf("emit: %s\n",node);
        if (node instanceof CopyInNode) {
            asm.copyToContext(((CopyInNode) node).getValue().getIndex(), ctx, depIn);
        } else if (node instanceof AllocateNode) {
            asm.allocate(((AllocateNode) node).getValue().getIndex(), ctx);
        } else if (node instanceof CopyOutNode) {
            asm.streamOutOfContext(((CopyOutNode) node).getValue().getValue().getIndex(), ctx, depIn);
        } else if (node instanceof StreamInNode) {
            asm.streamInToContext(((StreamInNode) node).getValue().getIndex(), ctx, depIn);
        } else if (node instanceof TaskNode) {
            final TaskNode taskNode = (TaskNode) node;

            asm.launch(gtid, taskNode.getContext().getDeviceIndex(), taskNode.getTaskIndex(), taskNode.getNumArgs(), depIn);
            gtid++;

            emitArgList(graph, context, taskNode);
        }
    }

    private void emitArgList(TornadoGraph graph, ExecutionContext context, TaskNode taskNode) {

        final int numArgs = taskNode.getNumArgs();
        for (int i = 0; i < numArgs; i++) {
            final AbstractNode argNode = taskNode.getArg(i);
            if (argNode instanceof ConstantNode) {
                asm.constantArg(((ConstantNode) argNode).getIndex());
            } else if (argNode instanceof CopyInNode) {
                asm.referenceArg(((CopyInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof StreamInNode) {
                asm.referenceArg(((StreamInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof CopyOutNode) {
                asm.referenceArg(((CopyOutNode) argNode).getValue().getValue().getIndex());
            } else if (argNode instanceof AllocateNode) {
                asm.referenceArg(((AllocateNode) argNode).getValue().getIndex());
            } else if (argNode instanceof DependentReadNode) {
                asm.referenceArg(((DependentReadNode) argNode).getValue().getIndex());
            }
        }

    }

    public void emitAddDep(int dep) {
        asm.addDependency(dep);
    }

    public void dump() {
        asm.dump();
    }

    public byte[] getCode() {
        return code;
    }

    public int getCodeSize() {
        return asm.position();
    }

}
