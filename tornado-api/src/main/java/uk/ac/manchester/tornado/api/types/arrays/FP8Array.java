/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.types.arrays;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.FP8;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Off-heap array of 8-bit floating-point (FP8) values — one byte per element — for low-precision
 * weight/activation storage on the GPU. Layout is identical to {@link Int8Array} (1 byte/element
 * plus the standard Tornado array header); the bytes are interpreted as FP8 through the
 * {@link FP8} codecs, in either OCP format: <b>E4M3</b> (default) or <b>E5M2</b>.
 *
 * <p>The element decoders ({@link #getE4M3(int)} / {@link #getE5M2(int)}) are pure arithmetic
 * (see {@link FP8}), so reading and dequantizing FP8 weights <em>inside a TornadoVM kernel</em>
 * compiles and runs on the CUDA backend with no special code generation — the same pattern the
 * Q8 dequant path uses. Host code fills the array with {@link #setE4M3(int, float)} /
 * {@link #setE5M2(int, float)} or writes raw bytes with {@link #set(int, byte)}.</p>
 */
@SegmentElementSize(size = 1)
public final class FP8Array extends TornadoNativeArray {

    private static final int FP8_BYTES = 1;

    private final int numberOfElements;
    private final int arrayHeaderSize;
    private final int baseIndex;
    private final long segmentByteSize;
    private final TornadoMemorySegment segment;

    /**
     * Allocates an FP8 array of {@code numberOfElements}, zero-initialized (FP8 all-zero byte is
     * {@code +0.0} in both formats).
     */
    public FP8Array(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / FP8_BYTES;
        this.segmentByteSize = (long) numberOfElements * FP8_BYTES + arrayHeaderSize;
        this.segment = new TornadoMemorySegment(segmentByteSize, numberOfElements);
    }

    private FP8Array(MemorySegment existingSegment) {
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / FP8_BYTES;
        long dataSize = existingSegment.byteSize() - arrayHeaderSize;
        this.numberOfElements = (int) (dataSize / FP8_BYTES);
        this.segmentByteSize = existingSegment.byteSize();
        this.segment = new TornadoMemorySegment(existingSegment);
        this.segment.getSegment().setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /** Wraps an existing header+data segment without copying. */
    public static FP8Array fromSegmentShallow(MemorySegment segment) {
        return new FP8Array(segment);
    }

    /** Builds an E4M3 array from float values (host-side encode). */
    public static FP8Array fromFloatsE4M3(float... values) {
        FP8Array a = new FP8Array(values.length);
        for (int i = 0; i < values.length; i++) {
            a.setE4M3(i, values[i]);
        }
        return a;
    }

    /** Builds an E5M2 array from float values (host-side encode). */
    public static FP8Array fromFloatsE5M2(float... values) {
        FP8Array a = new FP8Array(values.length);
        for (int i = 0; i < values.length; i++) {
            a.setE5M2(i, values[i]);
        }
        return a;
    }

    // ── Raw byte access ───────────────────────────────────────────────────────

    public void set(int index, byte value) {
        segment.setAtIndex(index, value, baseIndex);
    }

    public byte get(int index) {
        return segment.getByteAtIndex(index, baseIndex);
    }

    // ── FP8 float access ──────────────────────────────────────────────────────

    /** Decode element {@code index} as E4M3 → float. Kernel-safe. */
    public float getE4M3(int index) {
        return FP8.e4m3ToFloat(segment.getByteAtIndex(index, baseIndex));
    }

    /** Encode {@code value} to E4M3 and store at {@code index} (round-to-nearest-even, saturating). */
    public void setE4M3(int index, float value) {
        segment.setAtIndex(index, FP8.e4m3FromFloat(value), baseIndex);
    }

    /** Decode element {@code index} as E5M2 → float. Kernel-safe. */
    public float getE5M2(int index) {
        return FP8.e5m2ToFloat(segment.getByteAtIndex(index, baseIndex));
    }

    /** Encode {@code value} to E5M2 and store at {@code index} (round-to-nearest-even). */
    public void setE5M2(int index, float value) {
        segment.setAtIndex(index, FP8.e5m2FromFloat(value), baseIndex);
    }

    // ── TornadoNativeArray contract ───────────────────────────────────────────

    @Override
    public void clear() {
        for (int i = 0; i < numberOfElements; i++) {
            segment.setAtIndex(i, (byte) 0, baseIndex);
        }
    }

    @Override
    public int getElementSize() {
        return FP8_BYTES;
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return segment.getSegment().asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment.getSegment();
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }
}
