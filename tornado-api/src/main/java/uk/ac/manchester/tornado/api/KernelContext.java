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
}
