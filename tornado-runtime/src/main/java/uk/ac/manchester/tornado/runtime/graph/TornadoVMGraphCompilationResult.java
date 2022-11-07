/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.AllocateMultipleBuffersNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.AllocateNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ConstantNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyOutNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DeallocateNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ObjectNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.StreamInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;

public class TornadoVMGraphCompilationResult {

    public static final int MAX_TORNADO_VM_BYTECODE_SIZE = Integer.parseInt(getProperty("tornado.tvm.maxbytecodesize", "4096"));

    private final byte[] code;
    private final TornadoGraphAssembler bitcodeASM;
    private int globalTaskID;

    public TornadoVMGraphCompilationResult() {
        code = new byte[MAX_TORNADO_VM_BYTECODE_SIZE];
        bitcodeASM = new TornadoGraphAssembler(code);
        globalTaskID = 0;
    }

    public void begin(int numContexts, int numStacks, int numDeps) {
        bitcodeASM.setup(numContexts, numStacks, numDeps);
        for (int i = 0; i < numContexts; i++) {
            bitcodeASM.context(i);
        }
        bitcodeASM.begin();
    }

    public void barrier(int dep) {
        bitcodeASM.barrier(dep);
    }

    public void end() {
        bitcodeASM.end();
    }

    private void incTaskID() {
        globalTaskID++;
    }

    void emitAsyncNode(AbstractNode node, int contextID, int dependencyBC, long offset, long batchSize, long nThreads) {
        if (node instanceof AllocateMultipleBuffersNode) {
            bitcodeASM.allocate(((AllocateMultipleBuffersNode) node).getValues(), contextID, batchSize);
        } else if (node instanceof CopyInNode) {
            bitcodeASM.transferToDeviceOnce(((CopyInNode) node).getValue().getIndex(), contextID, dependencyBC, offset, batchSize);
        } else if (node instanceof AllocateNode) {
            TornadoLogger.info("[%s]: Skipping deprecated node %s", getClass().getSimpleName(), AllocateNode.class.getSimpleName());
        } else if (node instanceof CopyOutNode) {
            ObjectNode value = ((CopyOutNode) node).getValue().getValue();
            if (value != null) {
                bitcodeASM.transferToHost(value.getIndex(), contextID, dependencyBC, offset, batchSize);
            }
        } else if (node instanceof StreamInNode) {
            bitcodeASM.transferToDeviceAlways(((StreamInNode) node).getValue().getIndex(), contextID, dependencyBC, offset, batchSize);
        } else if (node instanceof DeallocateNode) {
            bitcodeASM.deallocate(((DeallocateNode) node).getValue().getIndex(), contextID);
        } else if (node instanceof TaskNode) {
            final TaskNode taskNode = (TaskNode) node;
            bitcodeASM.launch(globalTaskID, taskNode.getContext().getDeviceIndex(), taskNode.getTaskIndex(), taskNode.getNumArgs(), dependencyBC, offset, nThreads);
            emitArgList(taskNode);
            incTaskID();
        }
    }

    private void emitArgList(TaskNode taskNode) {
        final int numArgs = taskNode.getNumArgs();
        for (int i = 0; i < numArgs; i++) {
            final AbstractNode argNode = taskNode.getArg(i);
            if (argNode instanceof ConstantNode) {
                bitcodeASM.constantArg(argNode.getIndex());
            } else if (argNode instanceof CopyInNode) {
                bitcodeASM.referenceArg(((CopyInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof StreamInNode) {
                bitcodeASM.referenceArg(((StreamInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof CopyOutNode) {
                bitcodeASM.referenceArg(((CopyOutNode) argNode).getValue().getValue().getIndex());
            } else if (argNode instanceof AllocateNode) {
                bitcodeASM.referenceArg(((AllocateNode) argNode).getValue().getIndex());
            } else if (argNode instanceof DependentReadNode) {
                bitcodeASM.referenceArg(((DependentReadNode) argNode).getValue().getIndex());
            }
        }
    }

    public void emitAddDep(int dep) {
        bitcodeASM.addDependency(dep);
    }

    public void dump() {
        bitcodeASM.dump();
    }

    public byte[] getCode() {
        return code;
    }

    public int getCodeSize() {
        return bitcodeASM.position();
    }

}
