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
package tornado.runtime.api;

import java.nio.ByteOrder;
import tornado.common.DeviceFrame;
import tornado.runtime.cache.PrimitiveSerialiser;
import tornado.runtime.cache.TornadoByteBuffer;
import tornado.runtime.cache.TornadoDataMover.AllocateOp;
import tornado.runtime.cache.TornadoDataMover.AsyncOp;
import tornado.runtime.cache.TornadoDataMover.BarrierOp;
import tornado.runtime.cache.TornadoDataMover.SyncOp;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.RuntimeUtilities.isBoxedPrimitive;
import static tornado.common.Tornado.DEBUG;
import static tornado.common.Tornado.debug;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class TornadoCallStack extends TornadoByteBuffer implements DeviceFrame {

    public final static int RETURN_VALUE_INDEX = 0;
    public final static int DEOPT_VALUE_INDEX = 1;
    public final static int RESERVED_SLOTS = 6;

    private final int numArgs;

    private boolean onDevice;

    public TornadoCallStack(final int numArgs, final long bufferId, final long offset,
            final long numBytes, final long baseAddress,
            ByteOrder byteOrder, AllocateOp allocator,
            BarrierOp barrier,
            SyncOp<byte[]> write, AsyncOp<byte[]> enqueueWrite,
            SyncOp<byte[]> read, AsyncOp<byte[]> enqueueRead) {
        super(bufferId, offset, numBytes, baseAddress, byteOrder, allocator, barrier, write, enqueueWrite, read, enqueueRead);
        this.numArgs = numArgs;

        // clear the buffer and set the mark at the beginning of the arguments
        buffer.clear();
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(baseAddress + offset);
        buffer.putInt(0);
        buffer.putInt(numArgs);
        buffer.mark();

        onDevice = false;
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void syncWrite() {
        super.syncWrite();
        onDevice = true;
    }

    @Override
    public int asyncWrite() {
        return asyncWrite(null);
    }

    @Override
    public int asyncWrite(int[] events) {
        onDevice = true;
        return super.asyncWrite(events);
    }

    public int getReservedSlots() {
        return RESERVED_SLOTS;
    }

    public int getSlotCount() {
        return (int) bytes >> 3;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 2; i++) {
            buffer.putLong(i, 0);
        }

        buffer.reset();
        onDevice = false;
    }

    @Override
    public long getDeoptValue() {
        return buffer.getLong(8);
    }

    @Override
    public long getReturnValue() {
        syncRead();
        return buffer.getLong(0);
    }

    @Override
    public int getArgCount() {
        return buffer.getInt(10);
    }

    @Override
    public String toString() {
        return String
                .format("Call Stack: num args = %d, size = %s @ 0x%x (0x%x)",
                        numArgs,
                        humanReadableByteCount(bytes, true),
                        baseAddress, offset);
    }

    @Override
    public void dump() {
        super.dump(8);
    }

    @Override
    public void push(Object arg) {

        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else if (isBoxedPrimitive(arg)
                || arg.getClass().isPrimitive()) {
            if (DEBUG) {
                debug("arg : type=%s, value=%s", arg.getClass()
                        .getName(), arg.toString());
            }
            PrimitiveSerialiser.put(buffer, arg, 8);
        } else {
            shouldNotReachHere();
        }

    }

    @Override
    public void push(Object arg, long address) {

        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else {
            if (DEBUG) {
                debug("arg : [0x%x] type=%s, value=%s, address=0x%x", arg.hashCode(), arg.getClass()
                        .getSimpleName(), arg, address);
            }

            buffer.putLong(address);

        }

    }

}
