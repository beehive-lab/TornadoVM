/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Context of TornadoVM execution to exploit kernel-parallel applications, in
 * which the parallelism is implicit.
 * <p>
 * The application can access thread-id for 1D, 2D and 3D dimensions.
 * Additionally, the application can access local memory (OpenCL terminology),
 * or shared memory (CUDA terminology) as well as synchronization primitives
 * such as barriers.
 *
 * <p>
 * <ul>
 * <li>{@link KernelContext} is an object exposed by the TornadoVM API in order
 * to leverage low-level programming features provided by heterogeneous
 * frameworks (e.g. OpenCL, CUDA) to the developers, such as thread-id, access
 * to local/shared memory and barriers.</li>
 * <li>{@link KernelContext} provides a Java API that is transparently
 * translated to both OpenCL and PTX by the TornadoVM JIT compiler. The main
 * difference with the {@link TaskGraph} API is that the tasks within a
 * {@link TaskGraph} that use {@link KernelContext} must be
 * {@link GridScheduler}.</li>
 * </ul>
 * </p>
 */
public class KernelContext implements ExecutionContext {

    /**
     * It returns the thread identifier for the first dimension.
     * <p>
     * OpenCL equivalent: get_global_id(0);
     * <p>
     * PTX equivalent: blockIdx.x * blockDim.x + threadIdx.x
     */
    public final Integer globalIdx = 0;

    /**
     * It returns the thread identifier for the second dimension.
     * <p>
     * OpenCL equivalent: get_global_id(1);
     * <p>
     * PTX equivalent: blockIdx.y * blockDim.y + threadIdx.y
     */
    public final Integer globalIdy = 0;

    /**
     * It returns the thread identifier for the third dimension.
     * <p>
     * OpenCL equivalent: get_global_id(2);
     * <p>
     * PTX equivalent: blockIdx.z * blockDim.z + threadIdx.z
     */
    public final Integer globalIdz = 0;
    public final Integer groupIdx = 0;
    public final Integer groupIdy = 0;
    public final Integer groupIdz = 0;

    public final Integer localIdx = 0;
    public final Integer localIdy = 0;
    public final Integer localIdz = 0;

    /**
     * It returns the global group size of a particular dimension (e.g. X, Y, Z).
     * <p>
     * OpenCL equivalent: get_global_size();
     * <p>
     * PTX equivalent: gridDim * blockDim
     */
    public final Integer globalGroupSizeX = 0;
    public final Integer globalGroupSizeY = 0;
    public final Integer globalGroupSizeZ = 0;

    /**
     * It returns the global group size of a particular dimension (e.g. X, Y, Z).
     * <p>
     * OpenCL equivalent: get_local_size();
     * <p>
     * PTX equivalent: blockDim
     */
    public final Integer localGroupSizeX = 0;
    public final Integer localGroupSizeY = 0;
    public final Integer localGroupSizeZ = 0;

    /**
     * Class constructor specifying a particular {@link WorkerGrid} object.
     */
    public KernelContext() {

    }

    /**
     * Method used as a barrier to synchronize the order of memory operations to the
     * local memory (known as shared memory in PTX).
     * <p>
     * OpenCL equivalent: barrier(CLK_LOCAL_MEM_FENCE);
     * <p>
     * PTX equivalent: barrier.sync;
     */
    @Override
    public void localBarrier() {
    }

    /**
     * Method used as a barrier to synchronize the order of memory operations to the
     * global memory.
     * <p>
     * OpenCL equivalent: barrier(CLK_GLOBAL_MEM_FENCE);
     * <p>
     * PTX equivalent: barrier.sync;
     */
    @Override
    public void globalBarrier() {
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return int[]: reference to the int array
     */
    @Override
    public int[] allocateIntLocalArray(int size) {
        return new int[size];
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return byte[]: reference to the byte array
     */
    @Override
    public byte[] allocateByteLocalArray(int size) {
        return new byte[size];
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return HalfFloatArray: reference to the byte array
     */
    @Override
    public HalfFloat[] allocateHalfFloatLocalArray(int size) {
        return new HalfFloat[size];
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return long[]: reference to the int array
     */
    @Override
    public long[] allocateLongLocalArray(int size) {
        return new long[size];
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return float[]: reference to the int array
     */
    @Override
    public float[] allocateFloatLocalArray(int size) {
        return new float[size];
    }

    /**
     * It allocates a single dimensional array in local memory (known as shared
     * memory in PTX).
     *
     * @param size
     *     the size of the array
     * @return double[]: reference to the int array
     */
    @Override
    public double[] allocateDoubleLocalArray(int size) {
        return new double[size];
    }

    /**
     * Returns the sum of {@code val} across all active SIMD lanes.
     * <p>
     * Metal equivalent: {@code simd_sum(val)}<br>
     * PTX equivalent: butterfly reduction with {@code shfl.sync.down.b32}
     */
    public float simdSum(float val) {
        return val;
    }

    /**
     * Returns the value held by the thread {@code delta} lanes ahead in the SIMD group.
     * <p>
     * Metal equivalent: {@code simd_shuffle_down(val, delta)}<br>
     * PTX equivalent: {@code shfl.sync.down.b32 dest, val, delta, 31, 0xFFFFFFFF}
     */
    public float simdShuffleDown(float val, int delta) {
        return val;
    }

    /**
     * Broadcasts the value from lane 0 to all active SIMD lanes.
     * <p>
     * Metal equivalent: {@code simd_broadcast_first(val)}<br>
     * PTX equivalent: {@code shfl.sync.idx.b32 dest, val, 0, 31, 0xFFFFFFFF}
     */
    public float simdBroadcastFirst(float val) {
        return val;
    }

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(int* address, int val);
     */
    @Override
    public void atomicAdd(IntArray array, int index, int val) {
        int arrayValue = array.get(index);
        AtomicInteger atomicInteger = new AtomicInteger(arrayValue);
        array.set(index, atomicInteger.addAndGet(val));
    }

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(int* address, int val);
     */
    @Override
    public void atomicAdd(int[] array, int index, int val) {
        int arrayValue = array[index];
        AtomicInteger atomicInteger = new AtomicInteger(arrayValue);
        array[index] = atomicInteger.addAndGet(val);
    }

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(long* address, long val);
     */
    @Override
    public void atomicAdd(LongArray array, int index, long val) {
        long arrayValue = array.get(index);
        AtomicLong atomicLong = new AtomicLong(arrayValue);
        array.set(index, atomicLong.addAndGet(val));
    }

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(float* address, float val);
     */
    @Override
    public void atomicAdd(FloatArray array, int index, float val) {
    }

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(double* address, double val);
     */
    @Override
    public void atomicAdd(DoubleArray array, int index, double val) {
    }

    /**
     * Loads a half-float from a swizzled shared-memory tile (stride-32 layout).
     *
     * <p>Applies an XOR permutation to the logical {@code (row, col)} coordinate so that
     * the resulting shared-memory access pattern avoids bank conflicts. On NVIDIA GPUs, shared memory has
     * 32 banks of 4 bytes each; a naive row-major tile layout causes many threads in a warp
     * to hit the same bank, serializing the access. The XOR rotates each row's bank
     * assignment so consecutive rows spread across distinct banks.
     *
     * <p>The swizzle is computed on the <em>byte</em> offset:
     * <pre>{@code
     *   byteOffset = (row * stride + col) * 2          // fp16 = 2 bytes per element
     *   swizzled   = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4)
     * }</pre>
     *
     * <p>The three constants:
     * <ul>
     *   <li><b>7</b> (shift-right / source position): isolates the row-group bits to
     *       permute. The 32-bank * 4-byte layout means the bank pattern repeats every
     *       128 bytes (2<sup>7</sup>); shifting right by 7 yields the row group.</li>
     *   <li><b>0b111</b> (mask): three bits participate, giving 8 distinct row rotations.</li>
     *   <li><b>4</b> (shift-left / target position): the permutation lands at the 16-byte
     *       boundary (2<sup>4</sup>). Bits below 4 pass through untouched, so each 16-byte
     *       chunk stays contiguous.</li>
     * </ul>
     *
     * <p>The XOR is involutive, so {@link #swizzleStoreFp16Stride32} with the same
     * coordinate writes to exactly the position this method reads from.
     *
     * <p>The specific constants (a 16-byte permutation granularity over groups of 8 rows)
     * match the access pattern of NVIDIA Tensor Core matrix loads, so this layout is
     * intended for staging matrix tiles for future MMA support. It is, however, a general
     * bank-conflict-free layout usable by any kernel with the same access shape.
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of fp16 elements per row (16 for a 32-byte row)
     * @return the half-float stored at the swizzled position
     */
    public HalfFloat swizzleLoadFp16Stride32(HalfFloat[] arr, int row, int col, int stride) {
        int byteOffset = (row * stride + col) * 2;
        int swizzledByte = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4);
        return arr[swizzledByte / 2];
    }

    /**
     * Stores a half-float into a swizzled shared-memory tile (stride-32 layout).
     *
     * <p>The inverse of {@link #swizzleLoadFp16Stride32}: applies the identical XOR
     * permutation {@code byteOffset ^ (((byteOffset >> 7) & 0b111) << 4)} so a value written
     * at a given {@code (row, col)} is later read back from the same coordinate. See
     * {@link #swizzleLoadFp16Stride32} for the full derivation of the constants
     * (7 = source bits, 0b111 = mask, 4 = 16-byte target boundary).
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of fp16 elements per row (16 for a 32-byte row)
     * @param v      the half-float to store at the swizzled position
     */
    public void swizzleStoreFp16Stride32(HalfFloat[] arr, int row, int col, int stride, HalfFloat v) {
        int byteOffset = (row * stride + col) * 2;
        int swizzledByte = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4);
        arr[swizzledByte / 2] = v;
    }

    /**
     * Loads a half-float from a swizzled shared-memory tile (stride-16 layout).
     *
     * <p>Variant of {@link #swizzleLoadFp16Stride32} for a narrower tile (8 fp16 cols =
     * 16 bytes per row). Applies the same kind of bank-conflict-avoiding XOR permutation,
     * but with constants shifted down one bit because the row stride is halved:
     * <pre>{@code
     *   byteOffset = (row * stride + col) * 2          // fp16 = 2 bytes per element
     *   swizzled   = byteOffset ^ (((byteOffset >> 6) & 0b111) << 3)
     * }</pre>
     *
     * <p>The three constants:
     * <ul>
     *   <li><b>6</b> (shift-right / source position): row-group bits for a 16-byte row
     *       stride. The bank pattern spans half as many rows as the 32-byte case, so the
     *       group bits sit one position lower, at 2<sup>6</sup> = 64 bytes.</li>
     *   <li><b>0b111</b> (mask): three bits participate, giving 8 distinct row rotations.</li>
     *   <li><b>3</b> (shift-left / target position): the permutation lands at the 8-byte
     *       boundary (2<sup>3</sup>). Bits below 3 pass through untouched.</li>
     * </ul>
     *
     * <p>The XOR is involutive, so {@link #swizzleStoreFp16Stride16} with the same
     * coordinate writes to exactly the position this method reads from.
     *
     * <p>This narrower layout is intended for transposed matrix tiles in future MMA work.
     * The constants follow the same derivation as the stride-32 case, shifted down one bit
     * for the halved row stride, but have not yet been validated against a live consumer,
     * treat as provisional.
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of fp16 elements per row (8 for a 16-byte row)
     * @return the half-float stored at the swizzled position
     */
    public HalfFloat swizzleLoadFp16Stride16(HalfFloat[] arr, int row, int col, int stride) {
        int byteOffset = (row * stride + col) * 2;
        int swizzledByte = byteOffset ^ (((byteOffset >> 6) & 0b111) << 3);
        return arr[swizzledByte / 2];
    }

    /**
     * Stores a half-float into a swizzled shared-memory tile (stride-16 layout).
     *
     * <p>The inverse of {@link #swizzleLoadFp16Stride16}: applies the identical XOR
     * permutation {@code byteOffset ^ (((byteOffset >> 6) & 0b111) << 3)}. See
     * {@link #swizzleLoadFp16Stride16} for the constant derivation (6 = source bits,
     * 0b111 = mask, 3 = 8-byte target boundary) and the validation caveat.
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of fp16 elements per row (8 for a 16-byte row)
     * @param v      the half-float to store at the swizzled position
     */
    public void swizzleStoreFp16Stride16(HalfFloat[] arr, int row, int col, int stride, HalfFloat v) {
        int byteOffset = (row * stride + col) * 2;
        int swizzledByte = byteOffset ^ (((byteOffset >> 6) & 0b111) << 3);
        arr[swizzledByte / 2] = v;
    }

    /**
     * Loads an int8 value from a swizzled shared-memory tile.
     *
     * <p>Applies the same bank-conflict-avoiding XOR permutation as
     * {@link #swizzleLoadFp16Stride32}, with identical constants. The permutation operates
     * on a 16-byte granularity, which is independent of the element type, so the int8 and
     * fp16 stride-32 swizzles share the same math.
     *
     * <p>Because int8 elements are one byte, the logical element index <em>is</em> the byte
     * offset; there is no {@code * 2} conversion:
     * <pre>{@code
     *   byteOffset = row * stride + col
     *   swizzled   = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4)
     * }</pre>
     *
     * <p>Constants (see {@link #swizzleLoadFp16Stride32} for the full derivation):
     * <ul>
     *   <li><b>7</b> (source): row-group bits; the 32-bank * 4-byte layout repeats every
     *       128 bytes (2<sup>7</sup>).</li>
     *   <li><b>0b111</b> (mask): 8 distinct row rotations.</li>
     *   <li><b>4</b> (target): permutation at the 16-byte boundary (2<sup>4</sup>).</li>
     * </ul>
     *
     * <p>This single method serves both tile layouts; the caller selects via the
     * {@code stride} argument: 32 (bytes) for a wide tile, 16 (bytes) for a narrow one.
     * The swizzle math is identical for both. The layout is intended for staging matrix
     * tiles for future MMA support, but is a general bank-conflict-free layout usable by
     * any kernel with the same access shape.
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of int8 elements (= bytes) per row
     * @return the int8 value stored at the swizzled position
     */
    public byte swizzleLoadInt8(byte[] arr, int row, int col, int stride) {
        int byteOffset = row * stride + col;   // int8: element index == byte offset
        int swizzledByte = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4);
        return arr[swizzledByte];
    }

    /**
     * Stores an int8 value into a swizzled shared-memory tile.
     *
     * <p>The inverse of {@link #swizzleLoadInt8}: applies the identical XOR permutation
     * {@code byteOffset ^ (((byteOffset >> 7) & 0b111) << 4)}, where for int8 the element
     * index is the byte offset directly. See {@link #swizzleLoadInt8} for the constant
     * derivation and the dual-stride (32 / 16) usage.
     *
     * <p><b>Note:</b> Currently lowered on the PTX backend only.
     *
     * @param arr    the shared-memory tile, addressed in logical element coordinates
     * @param row    the row index within the tile
     * @param col    the column index within the row
     * @param stride the number of int8 elements (= bytes) per row
     * @param v      the int8 value to store at the swizzled position
     */
    public void swizzleStoreInt8(byte[] arr, int row, int col, int stride, byte v) {
        int byteOffset = row * stride + col;
        int swizzledByte = byteOffset ^ (((byteOffset >> 7) & 0b111) << 4);
        arr[swizzledByte] = v;
    }

}
