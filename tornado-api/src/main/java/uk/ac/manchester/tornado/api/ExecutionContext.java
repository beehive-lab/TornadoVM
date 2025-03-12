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

/**
 * Generic interface for TornadoVM to implement a Thread-Context API. This
 * interface allows the client to implement barriers, allocate memory in local
 * memory (OpenCL) or in shared memory (NVIDIA PTX).
 */
public interface ExecutionContext {

    /**
     * It adds a barrier for sync all threads within the same work-group to ensure
     * correct order in local memory (OpenCL terminology).
     *
     * <p>
     * Similar the OpenCL construct:
     * </p>
     *
     * <code>
     * barrier(CLK_LOCAL_MEM_FENCE);
     * </code>
     */
    void localBarrier();

    /**
     * It adds a barrier for sync all threads within the same work-group to ensure
     * correct order in global memory (OpenCL terminology).
     *
     * <p>
     * Similar the OpenCL construct:
     * </p>
     *
     * <code>
     * barrier(CLK_GLOBAL_MEM_FENCE);
     * </code>
     */
    void globalBarrier();

    /**
     * Array Allocation in Local Memory (OpenCL terminology).
     *
     * @param size
     *     size of the int-array.
     * @return int[]
     */
    int[] allocateIntLocalArray(int size);

    /**
     * Array Allocation in Local Memory (OpenCL terminology).
     *
     * @param size
     *     size of the long-array.
     * @return long[]
     */
    long[] allocateLongLocalArray(int size);

    /**
     * Array Allocation in Local Memory (OpenCL terminology).
     *
     * @param size
     *     size of the float-array.
     * @return float[]
     */
    float[] allocateFloatLocalArray(int size);

    /**
     * Array Allocation in Local Memory (OpenCL terminology).
     *
     * @param size
     *     size of the double-array.
     * @return double[]
     */
    double[] allocateDoubleLocalArray(int size);

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(int* address, int val);
     */
    void atomicAdd(IntArray array, int index, int val);

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(int* address, int val);
     */
    void atomicAdd(int[] array, int index, int val);

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(long* address, long val);
     */
    void atomicAdd(LongArray array, int index, long val);

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(float* address, float val);
     */
    void atomicAdd(FloatArray array, int index, float val);

    /**
     * Method used to read a memory address by using the array and the index,
     * then add the value of val to it, and write the result back to the same address.
     * <p>
     * PTX equivalent: atomicAdd(double* address, double val);
     */
    void atomicAdd(DoubleArray array, int index, double val);
}
