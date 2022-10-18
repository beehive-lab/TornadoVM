/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;

public class TornadoGraphAssembler {

    /**
     * TornadoVM Bytecode
     */
    public enum TornadoVMBytecode {

        /**
         * Native buffer allocation. If there is not enough space on the target device,
         * then we throw an exception.
         *
         * Format:
         *
         * <code>
         *     ALLOC(dest, numObjects, objects)
         * </code>
         */
        ALLOC((byte) 10),

        /**
         * Send data from Host -> Device only in the first execution of a task-graph.
         *
         * If there is no ALLOC associated with the TRANSFER_HOST_TO_DEVICE_ONCE-in, an
         * exception is launched.
         *
         * Format:
         *
         * <code>
         *      TRANSFER_HOST_TO_DEVICE_ONCE(obj, src, dest)
         * </code>
         *
         */
        TRANSFER_HOST_TO_DEVICE_ONCE((byte) 11),

        /**
         * Send data from Host -> Device in every execution of a task-graph. If there is
         * no ALLOC associated with the TRANSFER_HOST_TO_DEVICE_ALWAYS, an exception is
         * launched.
         *
         * Format:
         *
         * <code>
         *     TRANSFER_HOST_TO_DEVICE_ALWAYS(obj, src, dest)
         * </code>
         */
        TRANSFER_HOST_TO_DEVICE_ALWAYS((byte) 12),

        /**
         * Send data from Device -> Host in every execution of the task-graph. If there
         * is no ALLOC associated with the TRANSFER_DEVICE_TO_HOST_ALWAYS, an exception
         * is launched.
         *
         * Format:
         *
         * <code>
         *     TRANSFER_DEVICE_TO_HOST_ALWAYS(obj, src, dest)
         * </code>
         */
        TRANSFER_DEVICE_TO_HOST_ALWAYS((byte) 13),

        /**
         *
         */
        TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING((byte) 14), // TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING(obj, src, dest)

        /**
         * Compile the code the first iteration that the task-graph is executed and then
         * execute the kernel. If the kernel is already compiled, the kernel is directly
         * launched.
         *
         * Format:
         *
         * <code>
         *     LAUNCH(dep list index)
         * </code>
         */
        LAUNCH((byte) 15),

        /**
         * Sync point. The BC interpreter waits for an event to be finished before
         * continuing the execution.
         *
         * Format:
         *
         * <code>
         *     BARRIER <event>
         * </code>
         */
        BARRIER((byte) 16),

        /**
         * Initialization of a TornadoVM BC region.
         *
         * Format:
         *
         * <code>
         *     INIT(num contexts, num stacks, num dep lists)
         * </code>
         */
        INIT((byte) 17),

        /**
         * Execution initialization.
         */
        BEGIN((byte) 18),

        /**
         * Register an event to be used in a barrier bytecode.
         *
         * Format:
         *
         * <code>
         *     ADD_DEPENDENCY(list index)
         * </code>
         */
        ADD_DEPENDENCY((byte) 19),

        /**
         * Open an execution context. It needs a context-id (device-id).
         *
         * Format:
         *
         * <code>
         *     CONTEXT(ctx)
         * </code>
         */
        CONTEXT((byte) 20),

        /**
         * Close a bytecode region associated with a context.
         *
         * Format:
         *
         * <code>
         *     END(ctx)
         * </code>
         */
        END((byte) 21),

        /**
         * Add a constant value to be used as an argument for a compute-kernel.
         *
         * Format:
         *
         * <code>
         *     PUSH_CONSTANT_ARGUMENT(constant)
         * </code>
         */
        PUSH_CONSTANT_ARGUMENT((byte) 22),

        /**
         * Add a reference (e.g., a Java array reference) to be used as an argument for
         * a compute-kernel.
         *
         * Format:
         *
         * <code>
         *     PUSH_REFERENCE_ARGUMENT(reference)
         * </code>
         */
        PUSH_REFERENCE_ARGUMENT((byte) 23),

        /**
         * De-allocation of a buffer from a device
         *
         * Format:
         *
         * <code>
         *     DEALLOC(obj,dest)
         * </code>
         */
        DEALLOC((byte) 24);

        private final byte value;

        TornadoVMBytecode(byte value) {
            this.value = value;
        }

        public byte value() {
            return value;
        }
    }

    private final ByteBuffer buffer;

    TornadoGraphAssembler(byte[] code) {
        buffer = ByteBuffer.wrap(code);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public void reset() {
        buffer.rewind();
    }

    public int position() {
        return buffer.position();
    }

    void begin() {
        buffer.put(TornadoVMBytecode.BEGIN.value);
    }

    public void end() {
        buffer.put(TornadoVMBytecode.END.value);
    }

    void setup(int numContexts, int numStacks, int numDeps) {
        buffer.put(TornadoVMBytecode.INIT.value);
        buffer.putInt(numContexts);
        buffer.putInt(numStacks);
        buffer.putInt(numDeps);
    }

    void addDependency(int index) {
        buffer.put(TornadoVMBytecode.ADD_DEPENDENCY.value);
        buffer.putInt(index);
    }

    public void context(int index) {
        buffer.put(TornadoVMBytecode.CONTEXT.value);
        buffer.putInt(index);
    }

    public void allocate(List<AbstractNode> values, int ctx, long batchSize) {
        buffer.put(TornadoVMBytecode.ALLOC.value);
        buffer.putInt(ctx);
        buffer.putLong(batchSize);
        buffer.putInt(values.size());
        for (AbstractNode node : values) {
            buffer.putInt(node.getIndex());
        }
    }

    public void deallocate(int object, int ctx) {
        buffer.put(TornadoVMBytecode.DEALLOC.value);
        buffer.putInt(object);
        buffer.putInt(ctx);
    }

    void transferToDeviceOnce(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecode.TRANSFER_HOST_TO_DEVICE_ONCE.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void transferToDeviceAlways(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecode.TRANSFER_HOST_TO_DEVICE_ALWAYS.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void transferToHost(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecode.TRANSFER_DEVICE_TO_HOST_ALWAYS.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void launch(int gtid, int ctx, int task, int numParameters, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecode.LAUNCH.value);
        buffer.putInt(gtid);
        buffer.putInt(ctx);
        buffer.putInt(task);
        buffer.putInt(numParameters);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    public void barrier(int dep) {
        buffer.put(TornadoVMBytecode.BARRIER.value);
        buffer.putInt(dep);
    }

    void constantArg(int index) {
        buffer.put(TornadoVMBytecode.PUSH_CONSTANT_ARGUMENT.value);
        buffer.putInt(index);
    }

    void referenceArg(int index) {
        buffer.put(TornadoVMBytecode.PUSH_REFERENCE_ARGUMENT.value);
        buffer.putInt(index);
    }

    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s   \n", RuntimeUtilities.humanReadableByteCount(buffer.capacity(), true),
                RuntimeUtilities.humanReadableByteCount(buffer.position(), true));
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
