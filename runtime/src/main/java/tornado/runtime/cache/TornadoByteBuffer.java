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
package tornado.runtime.cache;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import tornado.runtime.cache.TornadoDataMover.AllocateOp;
import tornado.runtime.cache.TornadoDataMover.AsyncOp;
import tornado.runtime.cache.TornadoDataMover.BarrierOp;
import tornado.runtime.cache.TornadoDataMover.SyncOp;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.Tornado.warn;

/**
 * A buffer for inspecting data within an OpenCL device. It is not backed by any
 * userspace object.
 */
public class TornadoByteBuffer {

    protected final ByteBuffer buffer;
    protected final long bufferId;
//    protected final long bufferOffset;
    protected final long bytes;
    protected final long baseAddress;

    protected final long offset;

    private final BarrierOp barrierOp;
    private final SyncOp<byte[]> syncWriter;
    private final AsyncOp<byte[]> asyncWriter;
    private final SyncOp<byte[]> syncReader;
    private final AsyncOp<byte[]> asyncReader;

    public TornadoByteBuffer(final long bufferId, final long offset,
            final long numBytes, final long baseAddress,
            ByteOrder byteOrder, AllocateOp allocator,
            BarrierOp barrier,
            SyncOp<byte[]> write, AsyncOp<byte[]> enqueueWrite,
            SyncOp<byte[]> read, AsyncOp<byte[]> enqueueRead) {
        this.bufferId = bufferId;
        this.offset = offset;
        this.bytes = numBytes;
        this.baseAddress = baseAddress;
        this.barrierOp = barrier;
        this.syncWriter = write;
        this.asyncWriter = enqueueWrite;
        this.syncReader = read;
        this.asyncReader = enqueueRead;
        buffer = ByteBuffer.allocate((int) numBytes);
        buffer.order(byteOrder);
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void syncRead() {
        syncRead(null);
    }

    public void syncRead(final int[] events) {
        syncReader.apply(bufferId, offset, bytes, buffer.array(), events);
    }

    public int asyncRead() {
        return asyncRead(null);
    }

    public int asyncRead(final int[] events) {
        return asyncReader.apply(bufferId, offset, bytes, buffer.array(), events);
    }

    public void syncWrite() {
        syncWrite(null);
    }

    public void syncWrite(final int[] events) {
        syncWriter.apply(bufferId, offset, bytes, buffer.array(), events);
    }

    public int asyncWrite() {
        return asyncWrite(null);
    }

    public int asyncWrite(final int[] events) {
        return asyncWriter.apply(bufferId, offset, bytes, buffer.array(), events);
    }

    public int barrier() {
        return barrier(null);
    }

    public int barrier(int[] waitList) {
        return barrierOp.insert(null);
    }

    public void dump() {
        dump(64);
    }

    public void dump(final int width) {
        buffer.position(buffer.capacity());
        System.out.printf("Buffer  : capacity = %s, in use = %s\n",
                humanReadableByteCount(bytes, true),
                humanReadableByteCount(buffer.position(), true));
        for (int i = 0; i < buffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i + baseAddress);
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

    public byte get() {
        return buffer.get();
    }

    public ByteBuffer get(final byte[] dst) {
        return buffer.get(dst);
    }

    public ByteBuffer get(final byte[] dst, final int offset, final int length) {
        return buffer.get(dst, offset, length);
    }

    public byte get(final int index) {
        return buffer.get(index);
    }

    public int getAlignment() {
        return 64;
    }

    public long getBufferId() {
        return bufferId;
    }

    public long getBufferOffset() {
        return offset;
    }

    public char getChar() {
        return buffer.getChar();
    }

    public char getChar(final int index) {
        return buffer.getChar(index);
    }

    public double getDouble() {
        return buffer.getDouble();
    }

    public double getDouble(final int index) {
        return buffer.getDouble(index);
    }

    public float getFloat() {
        return buffer.getFloat();
    }

    public float getFloat(final int index) {
        return buffer.getFloat(index);
    }

    public int getInt() {
        return buffer.getInt();
    }

    public int getInt(final int index) {
        return buffer.getInt(index);
    }

    public long getLong() {
        return buffer.getLong();
    }

    public long getLong(final int index) {
        return buffer.getLong(index);
    }

    public short getShort() {
        return buffer.getShort();
    }

    public short getShort(final int index) {
        return buffer.getShort(index);
    }

    public long getSize() {
        return bytes;
    }

    public ByteBuffer put(final byte b) {
        return buffer.put(b);
    }

    public final ByteBuffer put(final byte[] src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final byte[] src, final int offset, final int length) {
        return buffer.put(src, offset, length);
    }

    public ByteBuffer put(final ByteBuffer src) {
        return buffer.put(src);
    }

    public ByteBuffer put(final int index, final byte b) {
        return buffer.put(index, b);
    }

    public ByteBuffer putChar(final char value) {
        return buffer.putChar(value);
    }

    public ByteBuffer putChar(final int index, final char value) {
        return buffer.putChar(index, value);
    }

    public ByteBuffer putDouble(final double value) {
        return buffer.putDouble(value);
    }

    public ByteBuffer putDouble(final int index, final double value) {
        return buffer.putDouble(index, value);
    }

    public ByteBuffer putFloat(final float value) {
        return buffer.putFloat(value);
    }

    public ByteBuffer putFloat(final int index, final float value) {
        return buffer.putFloat(index, value);
    }

    public ByteBuffer putInt(final int value) {
        return buffer.putInt(value);
    }

    public ByteBuffer putInt(final int index, final int value) {
        return buffer.putInt(index, value);
    }

    public ByteBuffer putLong(final int index, final long value) {
        return buffer.putLong(index, value);
    }

    public ByteBuffer putLong(final long value) {
        return buffer.putLong(value);
    }

    public ByteBuffer putShort(final int index, final short value) {
        return buffer.putShort(index, value);
    }

    public ByteBuffer putShort(final short value) {
        return buffer.putShort(value);
    }

    public long toBuffer() {
        return bufferId;
    }

    public Object value() {
        return buffer.array();
    }

    public void zeroMemory() {
        warn("zero memory unimplemented");
    }
}
