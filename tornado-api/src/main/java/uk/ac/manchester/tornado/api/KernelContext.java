/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.annotations.TornadoVMIntrinsic;

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
 * difference with the {@link TaskSchedule} API is that the tasks within a
 * {@link TaskSchedule} that use {@link KernelContext} must be
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
     *            the size of the array
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
     *            the size of the array
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
     *            the size of the array
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
     *            the size of the array
     * @return double[]: reference to the int array
     */
    @Override
    public double[] allocateDoubleLocalArray(int size) {
        return new double[size];
    }

    /**
     * It returns the local group size of the associated WorkerGrid for a particular
     * dimension.
     * <p>
     * OpenCL equivalent: get_local_size();
     * <p>
     * PTX equivalent: blockDim
     */
    @TornadoVMIntrinsic
    public int getLocalGroupSize(int dim) {
        return 0;
    }

    /**
     * It returns the global group size of the associated WorkerGrid for a
     * particular dimension.
     * <p>
     * OpenCL equivalent: get_global_size();
     * <p>
     * PTX equivalent: gridDim * blockDim
     */
    @TornadoVMIntrinsic
    public int getGlobalGroupSize(int dim) {
        return 0;
    }
}