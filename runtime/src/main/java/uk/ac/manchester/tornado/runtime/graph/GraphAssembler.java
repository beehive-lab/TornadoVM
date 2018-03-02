/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.ac.manchester.tornado.common.RuntimeUtilities;

public class GraphAssembler {

    public final static byte ALLOCATE = 20; // ALLOCATE(obj,dest)
    public final static byte COPY_IN = 21; // COPY(obj, src, dest)
    public final static byte STREAM_IN = 22; // STREAM_IN(obj, src, dest)
    public final static byte STREAM_OUT = 23; // STREAM_OUT(obj, src, dest)
    public final static byte STREAM_OUT_BLOCKING = 24; // STREAM_OUT(obj, src, dest)

    public final static byte LAUNCH = 28; // LAUNCH(dep list index)

    public final static byte BARRIER = 50;

    public final static byte SETUP = 10;  //BEGIN(num contexts, num stacks, num dep lists)

    public final static byte BEGIN = 11;
    public final static byte ADD_DEP = 12; // ADD_DEP(list index)
    public final static byte CONTEXT = 13; // CONTEXT(ctx)
    public final static byte END = 0;

    public final static byte CONSTANT_ARG = 32;
    public final static byte REFERENCE_ARG = 33;

    private final ByteBuffer buffer;

    public GraphAssembler(byte[] code) {
        buffer = ByteBuffer.wrap(code);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public void reset() {
        buffer.rewind();
    }

    public int position() {
        return buffer.position();
    }

    public void begin() {
        buffer.put(BEGIN);
    }

    public void end() {
        buffer.put(END);
    }

    public void setup(int numContexts, int numStacks, int numDeps) {
        buffer.put(SETUP);
        buffer.putInt(numContexts);
        buffer.putInt(numStacks);
        buffer.putInt(numDeps);
    }

    public void addDependency(int index) {
        buffer.put(ADD_DEP);
        buffer.putInt(index);
    }

    public void context(int index) {
        buffer.put(CONTEXT);
        buffer.putInt(index);
    }

    public void allocate(int object, int ctx) {
        buffer.put(ALLOCATE);
        buffer.putInt(object);
        buffer.putInt(ctx);
    }

    public void copyToContext(int obj, int ctx, int dep) {
        buffer.put(COPY_IN);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
    }

    public void streamInToContext(int obj, int ctx, int dep) {
        buffer.put(STREAM_IN);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
    }

    public void streamOutOfContext(int obj, int ctx, int dep) {
        buffer.put(STREAM_OUT);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
    }

    public void launch(int gtid, int ctx, int task, int numParameters, int dep) {
        buffer.put(LAUNCH);
        buffer.putInt(gtid);
        buffer.putInt(ctx);
        buffer.putInt(task);
        buffer.putInt(numParameters);
        buffer.putInt(dep);
    }

    public void barrier(int dep) {
        buffer.put(BARRIER);
        buffer.putInt(dep);
    }

    public void constantArg(int index) {
        buffer.put(CONSTANT_ARG);
        buffer.putInt(index);
    }

    public void referenceArg(int index) {
        buffer.put(REFERENCE_ARG);
        buffer.putInt(index);
    }

    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s \n",
                RuntimeUtilities.humanReadableByteCount(buffer.capacity(),
                        true), RuntimeUtilities.humanReadableByteCount(
                        buffer.position(), true));
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
