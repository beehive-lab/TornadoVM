/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
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

/**
 * TornadoVMContext: Advanced programming features on top of the TaskSchedule
 * API.
 * <p>
 * </> TornadoVMContext is an object exposed by the TornadoVM API in order to
 * expose the low-level programming features provided by heterogeneous
 * frameworks (e.g. OpenCL, CUDA) to the developers. TornadoVMContext provides a
 * Java API that is transparently translated to both OpenCL and PTX by the
 * TornadoVM JIT compiler. The main difference with the TaskSchedule API is that
 * the tasks within a TaskSchedule that use TornadoVMContext must be GridTasks.
 * </p>
 */
public class TornadoVMContext implements ExecutionContext {

    public final Integer threadIdx = 0;
    public final Integer threadIdy = 0;
    public final Integer threadIdz = 0;
    public final Integer groupIdx = 0;
    public final Integer groupIdy = 0;
    public final Integer groupIdz = 0;

    public final Integer localIdx = 0;
    public final Integer localIdy = 0;
    public final Integer localIdz = 0;

    private WorkerGrid grid;

    /**
     * Class constructor specifying a particular WorkerGrid object.
     */
    public TornadoVMContext(WorkerGrid grid) {
        this.grid = grid;
    }

    /**
     * WorkerGrid is used to indicate scheduling information regarding the execution
     * of the tasks withing a TaskSchedule. For example, the dimensions, the global
     * work size, the local work size.
     * 
     * @return the WorkerGrid which is associated to TornadoVMContext
     */
    @Override
    public WorkerGrid getGrid() {
        return this.grid;
    }

    /**
     * localBarrier() is used as a barrier to synchronize the order of memory
     * operations to the local memory (known as shared memory in PTX).
     * <p>
     * OpenCL equivalent: barrier(CLK_LOCAL_MEM_FENCE);
     * 
     * PTX equivalent: barrier.sync;
     */
    @Override
    public void localBarrier() {
    }

    /**
     * globalBarrier() is used as a barrier to synchronize the order of memory
     * operations to the global memory.
     * <p>
     * OpenCL equivalent: barrier(CLK_GLOBAL_MEM_FENCE);
     *
     * PTX equivalent: barrier.sync;
     */
    @Override
    public void globalBarrier() {

    }

    /**
     * allocateIntLocalArray() allocates a single dimensional array in local memory
     * (known as shared memory in PTX).
     *
     * @param size
     *            the size of the array
     * @return reference to the int array
     */
    @Override
    public int[] allocateIntLocalArray(int size) {
        return new int[size];
    }

    /**
     * allocateLongLocalArray() allocates a single dimensional array in local memory
     * (known as shared memory in PTX).
     *
     * @param size
     *            the size of the array
     * @return reference to the long array
     */
    @Override
    public long[] allocateLongLocalArray(int size) {
        return new long[size];
    }

    /**
     * allocateFloatLocalArray() allocates a single dimensional array in local
     * memory (known as shared memory in PTX).
     *
     * @param size
     *            the size of the array
     * @return reference to the float array
     */
    @Override
    public float[] allocateFloatLocalArray(int size) {
        return new float[size];
    }

    /**
     * allocateDoubleLocalArray() allocates a single dimensional array in local
     * memory (known as shared memory in PTX).
     *
     * @param size
     *            the size of the array
     * @return reference to the double array
     */
    @Override
    public double[] allocateDoubleLocalArray(int size) {
        return new double[size];
    }

    @Override
    public void launch(FunctionalInterface f, WorkerGrid grid) {

    }

    /**
     * getX() returns the thread identifier for the first dimension.
     * <p>
     * OpenCL equivalent: get_global_id(0);
     * 
     * PTX equivalent: blockIdx * blockDim + threadIdx
     *
     * @return the thread identifier of the first dimension
     */
    public int getX() {
        return threadIdx;
    }

    /**
     * getY() returns the thread identifier for the second dimension.
     * <p>
     * OpenCL equivalent: get_global_id(1);
     *
     * PTX equivalent: blockIdy * blockDim + threadIdy
     *
     * @return the thread identifier of the second dimension
     */
    public int getY() {
        return threadIdy;
    }

    /**
     * getZ() returns the thread identifier for the third dimension.
     * <p>
     * OpenCL equivalent: get_global_id(2);
     *
     * PTX equivalent: blockIdz * blockDim + threadIdz
     *
     * @return the thread identifier of the third dimension
     */
    public int getZ() {
        return threadIdz;
    }

    /**
     * getLocalGroupSize(dim) returns the local group size of the associated
     * WorkerGrid for a particular dimension.
     * <p>
     * OpenCL equivalent: get_local_size();
     *
     * PTX equivalent: blockDim
     *
     * @return the number of local threads for a dimension
     */
    public int getLocalGroupSize(int dim) {
        return (int) grid.getLocalWork()[dim];
    }

    /**
     * getGlobalGroupSize(dim) returns the global group size of the associated
     * WorkerGrid for a particular dimension.
     * <p>
     * OpenCL equivalent: get_global_size();
     *
     * PTX equivalent: gridDim * blockDim
     * 
     * @return the number of global threads for a dimension
     */
    public int getGlobalGroupSize(int dim) {
        return (int) grid.getGlobalWork()[dim];
    }
}