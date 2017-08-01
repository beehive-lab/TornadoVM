/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import tornado.common.RuntimeUtilities;
import tornado.common.TornadoDevice.BlockingMode;
import tornado.common.TornadoDevice.CacheMode;
import tornado.common.TornadoDevice.SharingMode;

public class GraphAssembler {

    /*
     * SETUP initialises the execution context on the VM
     */
    public final static byte SETUP = 10;  //BEGIN(num contexts, num stacks, num dep lists)

    /*
     * CONTEXT initialises a context for the VM
     */
    public final static byte CONTEXT = 11; // CONTEXT(ctx)

    /*
     * BEGIN and END ops signifies start and end of executable ops repsectively
     */
    public final static byte BEGIN = 12;
    public final static byte END = 0;

    /*
     * Allocate op ensures that an object is allocated on the device
     */
    public final static byte ALLOCATE = 20; // ALLOCATE(obj,dest)
    public final static byte ALLOCATE_OR_COPY = 21; // ALLOCATE_OR_COPY(object,dest)

    /*
     * Standard COPY_IN nodes ops always copy data to the device
     */
    public final static byte READ_HOST = 22; // READ_HOST(mode,obj, src, dest)
    public final static byte WRITE_HOST = 23; // WRITE_HOST(mode,obj, src, dest)
    public final static byte PRE_FETCH = 24; // PRE_FETCH(index);
    public final static byte POST_FETCH = 25; // POST_FETCH(index);

    /*
     * LAUNCH op schedules the task for execution after all the operations in
     * the dependency list have completed.
     */
    public final static byte LAUNCH = 28; // LAUNCH(global id, context, task, num args,events list)

    /*
     * PRIMITIVE_ARG and REFERENCE_ARG are used to specify the types of each
     * argument in the argument list encoded in the LAUNCH op
     */
    public final static byte PUSH_ARG = 31;
    public final static byte PRIMITIVE_ARG = 32;
    public final static byte REFERENCE_ARG = 33;

    /*
     * PUSH_EVENT_Q adds the last event to a specified dependency list
     */
    public final static byte PUSH_EVENT_Q = 29; // PUSH_EVENT_Q(list index)

    /*
     * Synchronisation barrier between device ops
     */
    public final static byte BARRIER = 50;

    public final static byte BLOCKING_MODE = 0x1;
    public final static byte CACHE_MODE = 0x2;
    public final static byte SHARING_MODE = 0x4;

    public static byte encodeBlockingMode(BlockingMode mode, byte value) {
        return (byte) (mode == BlockingMode.NON_BLOCKING ? value : value | BLOCKING_MODE);
    }

    public static byte encodeCacheMode(CacheMode mode, byte value) {
        return (byte) (mode == CacheMode.NON_CACHEABLE ? value : value | CACHE_MODE);
    }

    public static byte encodeSharingMode(SharingMode mode, byte value) {
        return (byte) (mode == SharingMode.SHARED ? value : value | SHARING_MODE);
    }

    public static BlockingMode decodeBlockingMode(byte value) {
        return (value & BLOCKING_MODE) == 0 ? BlockingMode.NON_BLOCKING : BlockingMode.BLOCKING;
    }

    public static CacheMode decodeCacheMode(byte value) {
        return (value & CACHE_MODE) == 0 ? CacheMode.NON_CACHEABLE : CacheMode.CACHABLE;
    }

    public static SharingMode decodeSharingMode(byte value) {
        return (value & SHARING_MODE) == 0 ? SharingMode.SHARED : SharingMode.EXCLUSIVE;
    }

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
        buffer.put(PUSH_EVENT_Q);
        buffer.putInt(index);
    }

    public void context(int index) {
        buffer.put(CONTEXT);
        buffer.putInt(index);
    }

    public void allocate(int object, int ctx) {
        buffer.put(ALLOCATE_OR_COPY);
        buffer.putInt(object);
        buffer.putInt(ctx);
    }

    public void allocateOrCopy(int object, int ctx) {
        buffer.put(ALLOCATE_OR_COPY);
        buffer.putInt(object);
        buffer.putInt(ctx);
    }

    public void readHost(byte mode, int obj, int ctx, int dep) {
        buffer.put(READ_HOST);
        buffer.put(mode);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
    }

    public void writeHost(byte mode, int obj, int ctx, int dep) {
        buffer.put(WRITE_HOST);
        buffer.put(mode);
        buffer.putInt(obj);
        buffer.putInt(ctx);
        buffer.putInt(dep);
    }

    public void launch(byte mode, int gtid, int ctx, int task, int numParameters, int dep) {
        buffer.put(LAUNCH);
        buffer.put(mode);
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

    public void pushArg(int index) {
        buffer.put(PUSH_ARG);
        buffer.putInt(index);
    }

    public void constantArg(int index) {
        buffer.put(PRIMITIVE_ARG);
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

    void postfetch(int index) {
        buffer.put(POST_FETCH);
        buffer.putInt(index);
    }

}
