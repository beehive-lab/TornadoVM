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

    public enum TornadoVMBytecodes {
        // @formatter:off
        PERSIST((byte) 10),             // PERSIST(dest, numObjects, objects)
        COPY_IN((byte) 11),             // COPY(obj, src, dest)
        STREAM_IN((byte) 12),           // STREAM_IN(obj, src, dest)
        STREAM_OUT((byte) 13),          // STREAM_OUT(obj, src, dest)
        STREAM_OUT_BLOCKING((byte) 14), // STREAM_OUT(obj, src, dest)
        LAUNCH((byte) 15),              // LAUNCH(dep list index)
        BARRIER((byte) 16),             // BARRIER <events>
        SETUP((byte) 17),
        BEGIN((byte) 18),               // BEGIN(num contexts, num stacks, num dep lists)
        ADD_DEP((byte) 19),             // ADD_DEP(list index)
        CONTEXT((byte) 20),             // CONTEXT(ctx)
        END((byte) 21),                 // END(ctx)
        CONSTANT_ARGUMENT((byte) 22),
        REFERENCE_ARGUMENT((byte) 23),
        DEALLOCATE((byte) 24);          // DEALLOCATE(obj,dest)
        // @formatter:on

        private byte value;

        TornadoVMBytecodes(byte value) {
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
        buffer.put(TornadoVMBytecodes.BEGIN.value);
    }

    public void end() {
        buffer.put(TornadoVMBytecodes.END.value);
    }

    void setup(int numContexts, int numStacks, int numDeps) {
        buffer.put(TornadoVMBytecodes.SETUP.value);
        buffer.putInt(numContexts);
        buffer.putInt(numStacks);
        buffer.putInt(numDeps);
    }

    void addDependency(int index) {
        buffer.put(TornadoVMBytecodes.ADD_DEP.value);
        buffer.putInt(index);
    }

    public void context(int index) {
        buffer.put(TornadoVMBytecodes.CONTEXT.value);
        buffer.putInt(index);
    }

    public void persist(List<AbstractNode> values, int ctx, long batchSize) {
        buffer.put(TornadoVMBytecodes.PERSIST.value);
        buffer.putInt(ctx);
        buffer.putLong(batchSize);
        buffer.putInt(values.size());
        for (AbstractNode node : values) {
            buffer.putInt(node.getIndex());
        }
    }

    public void deallocate(int object, int ctx) {
        buffer.put(TornadoVMBytecodes.DEALLOCATE.value);
        buffer.putInt(object);
        buffer.putInt(ctx);
    }

    void copyToContext(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecodes.COPY_IN.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void streamInToContext(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecodes.STREAM_IN.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void streamOutOfContext(int obj, int ctx, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecodes.STREAM_OUT.value);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
        buffer.putLong(offset);
        buffer.putLong(size);
    }

    void launch(int gtid, int ctx, int task, int numParameters, int dep, long offset, long size) {
        buffer.put(TornadoVMBytecodes.LAUNCH.value);
        buffer.putInt(gtid);
        buffer.putInt(ctx);
        buffer.putInt(task);
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
        buffer.put(TornadoVMBytecodes.CONSTANT_ARGUMENT.value);
        buffer.putInt(index);
    }

    void referenceArg(int index) {
        buffer.put(TornadoVMBytecodes.REFERENCE_ARGUMENT.value);
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
