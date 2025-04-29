/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022-2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
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
import uk.ac.manchester.tornado.runtime.graph.nodes.OnDeviceObjectNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.PersistedObjectNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.StreamInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;

public class TornadoVMBytecodeBuilder {

    public static final int MAX_TORNADO_VM_BYTECODE_SIZE = Integer.parseInt(getProperty("tornado.tvm.maxbytecodesize", "4096"));

    private final byte[] code;

    private final TornadoVMBytecodeAssembler bitcodeASM;

    private boolean isSingleContext;

    /**
     * It constructs a new TornadoVMBytecodeBuilder instance. Initializes the byte
     * array to hold the bytecode with the maximum bytecode size. Initializes the
     * TornadoVMBytecodeAssembler with the byte array.
     */
    public TornadoVMBytecodeBuilder(boolean isSingleContext) {
        code = new byte[MAX_TORNADO_VM_BYTECODE_SIZE];
        bitcodeASM = new TornadoVMBytecodeAssembler(code);
        this.isSingleContext = isSingleContext;
    }

    public boolean isSingleContext() {
        return isSingleContext;
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

    void emitAsyncNode(AbstractNode node, int dependencyBC, long offset, long batchSize, long nThreads) {
        if (node instanceof AllocateMultipleBuffersNode allocateMultipleBuffersNode) {
            bitcodeASM.allocate(allocateMultipleBuffersNode.getValues(), batchSize);
        } else if (node instanceof OnDeviceObjectNode onDeviceObjectNode) {
            bitcodeASM.onDevice(onDeviceObjectNode.getValue().getIndex(), dependencyBC);
        } else if (node instanceof CopyInNode copyInNode) {
            bitcodeASM.transferToDeviceOnce(copyInNode.getValue().getIndex(), dependencyBC, offset, batchSize);
        } else if (node instanceof AllocateNode allocateNode) {
            new TornadoLogger().info("[%s]: Skipping deprecated node %s", getClass().getSimpleName(), AllocateNode.class.getSimpleName());
        } else if (node instanceof CopyOutNode copyOutNode) {
            ObjectNode value = copyOutNode.getValue().getValue();
            bitcodeASM.transferToHost(value.getIndex(), dependencyBC, offset, batchSize);
        } else if (node instanceof StreamInNode streamInNode) {
            bitcodeASM.transferToDeviceAlways(streamInNode.getValue().getIndex(), dependencyBC, offset, batchSize);
        } else if (node instanceof DeallocateNode deallocateNode) {
            bitcodeASM.deallocate((deallocateNode.getValue().getIndex()));
        } else if (node instanceof PersistedObjectNode persistedObjectNode) {
            bitcodeASM.persist(persistedObjectNode.getValue().getIndex(), dependencyBC);
        } else if (node instanceof TaskNode taskNodee) {
            final TaskNode taskNode = taskNodee;
            bitcodeASM.launch(taskNode.getContext().getDeviceIndex(), taskNode.getTaskIndex(), taskNode.getNumArgs(), dependencyBC, offset, nThreads);
            emitArgList(taskNode);
        }
    }

    private void emitArgList(TaskNode taskNode) {
        final int numArgs = taskNode.getNumArgs();
        for (int i = 0; i < numArgs; i++) {
            final AbstractNode argNode = taskNode.getArg(i);
            if (argNode instanceof ConstantNode) {
                bitcodeASM.constantArg(argNode.getIndex());
            } else if (argNode instanceof CopyInNode copyInNode) {
                bitcodeASM.referenceArg(copyInNode.getValue().getIndex());
            } else if (argNode instanceof OnDeviceObjectNode onDeviceObjectNode) {
                bitcodeASM.referenceArg(onDeviceObjectNode.getValue().getIndex());
            } else if (argNode instanceof StreamInNode streamInNode) {
                bitcodeASM.referenceArg(streamInNode.getValue().getIndex());
            } else if (argNode instanceof CopyOutNode copyOutNode) {
                bitcodeASM.referenceArg(copyOutNode.getValue().getValue().getIndex());
            } else if (argNode instanceof AllocateNode allocateNode) {
                bitcodeASM.referenceArg(allocateNode.getValue().getIndex());
            } else if (argNode instanceof DependentReadNode dependentReadNode) {
                bitcodeASM.referenceArg(dependentReadNode.getValue().getIndex());
            }
        }
    }

    public void emitAddDependency(int dep) {
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

    public int getLastCopyOutPosition() {
        return bitcodeASM.getLastCopyOutPosition();
    }

    private static class TornadoVMBytecodeAssembler {
        private final ByteBuffer buffer;
        private int lastCopyOutPosition;

        /**
         * It constructs a new {@link TornadoVMBytecodeAssembler} instance.
         *
         * @param code
         *     The byte array to hold the assembled bytecode.
         */
        TornadoVMBytecodeAssembler(byte[] code) {
            buffer = ByteBuffer.wrap(code);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        public int position() {
            return buffer.position();
        }

        void begin() {
            buffer.put(TornadoVMBytecodes.BEGIN.value);
        }

        public void end() {
            buffer.put(TornadoVMBytecodes.END.value);
        }

        void setup(int numContexts, int numStacks, int numDeps) {
            buffer.put(TornadoVMBytecodes.INIT.value);
            buffer.putInt(numContexts);
            buffer.putInt(numStacks);
            buffer.putInt(numDeps);
        }

        void addDependency(int index) {
            buffer.put(TornadoVMBytecodes.ADD_DEPENDENCY.value);
            buffer.putInt(index);
        }

        public void context(int index) {
            buffer.put(TornadoVMBytecodes.CONTEXT.value);
            buffer.putInt(index);
        }

        public void allocate(List<AbstractNode> values, long batchSize) {
            buffer.put(TornadoVMBytecodes.ALLOC.value);
            buffer.putLong(batchSize);
            buffer.putInt(values.size());
            for (AbstractNode node : values) {
                buffer.putInt(node.getIndex());
            }
        }

        public void onDevice(int object, int dep) {
            buffer.put(TornadoVMBytecodes.ON_DEVICE.value);
            buffer.putInt(object);
            buffer.putInt(dep);
        }

        public void persist(int object, int dep) {
            buffer.put(TornadoVMBytecodes.PERSIST.value);
            buffer.putInt(object);
            buffer.putInt(dep);
        }

        public void deallocate(int object) {
            buffer.put(TornadoVMBytecodes.DEALLOC.value);
            buffer.putInt(object);
        }

        void transferToDeviceOnce(int obj, int dep, long offset, long size) {
            buffer.put(TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE.value);
            buffer.putInt(obj);
            buffer.putInt(dep);
            buffer.putLong(offset);
            buffer.putLong(size);
        }

        void transferToDeviceAlways(int obj, int dep, long offset, long size) {
            buffer.put(TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS.value);
            buffer.putInt(obj);
            buffer.putInt(dep);
            buffer.putLong(offset);
            buffer.putLong(size);
        }

        void transferToHost(int obj, int dep, long offset, long size) {
            lastCopyOutPosition = buffer.position();
            buffer.put(TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value);
            buffer.putInt(obj);
            buffer.putInt(dep);
            buffer.putLong(offset);
            buffer.putLong(size);
        }

        void launch(int callStackDeviceIndex, int taskIndex, int numParameters, int dep, long offset, long size) {
            buffer.put(TornadoVMBytecodes.LAUNCH.value);
            buffer.putInt(callStackDeviceIndex);
            buffer.putInt(taskIndex);
            buffer.putInt(numParameters);
            buffer.putInt(dep);
            buffer.putLong(offset);
            buffer.putLong(size);
        }

        public void barrier(int dep) {
            buffer.put(TornadoVMBytecodes.BARRIER.value);
            buffer.putInt(dep);
        }

        void constantArg(int index) {
            buffer.put(TornadoVMBytecodes.PUSH_CONSTANT_ARGUMENT.value);
            buffer.putInt(index);
        }

        void referenceArg(int index) {
            buffer.put(TornadoVMBytecodes.PUSH_REFERENCE_ARGUMENT.value);
            buffer.putInt(index);
        }

        int getLastCopyOutPosition() {
            return lastCopyOutPosition;
        }

        /**
         * Dumps the assembled bytecode by printing it to the console.
         */
        public void dump() {
            final int width = 16;
            System.out.printf("code  : capacity = %s, in use = %s   \n", RuntimeUtilities.humanReadableByteCount(buffer.capacity(), true), RuntimeUtilities.humanReadableByteCount(buffer.position(),
                    true));
            for (int i = 0; i < buffer.position(); i += width) {
                System.out.printf("[0x%04x]: ", i);
                for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
                    if (j % 2 == 0) {
                        System.out.printf(" ");
                    }
                    if (j < buffer.position() - i) {
                        System.out.printf("%02x", buffer.get(i + j));
                    } else {
                        System.out.printf("..");
                    }
                }
                System.out.println();
            }
        }
    }
}
