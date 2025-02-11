/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.common.CommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;

public abstract class SPIRVContext {

    SPIRVPlatform platform;
    List<SPIRVDevice> devices;

    public SPIRVContext(SPIRVPlatform platform, List<SPIRVDevice> devices) {
        this.platform = platform;
        this.devices = devices;
    }

    public abstract SPIRVDeviceContext getDeviceContext(int deviceIndex);

    public abstract CommandQueue getCommandQueueForDevice(long executionPlanId, int deviceIndex);

    public abstract OCLContextInterface getOpenCLLayer();

    public abstract OCLEventPool getOCLEventPool(long executionPlanId);

    public abstract long allocateMemory(int deviceIndex, long numBytes, Access access);

    public abstract void freeMemory(long buffer, int deviceIndex);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract int enqueueWriteBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long value, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract void enqueueBarrier(long executionPlanId, int deviceIndex);

    public abstract void flush(long executionPlanId, int deviceIndex);

    public abstract void readBuffer(long executionPlanId, int deviceIndex, long bufferId, long offset, long bytes, long offHeapSegmentAddress, long hostOffset, int[] waitEvents,
            ProfilerTransfer profilerTransfer);

    public abstract void reset(long executionPlanId, int deviceIndex);

    public abstract long mapOnDeviceMemoryRegion(long executionPlanId, int deviceIndex, long destBuffer, long srcBuffer, long offset, int sizeOfType, long sizeSource, long sizeDest);
}
