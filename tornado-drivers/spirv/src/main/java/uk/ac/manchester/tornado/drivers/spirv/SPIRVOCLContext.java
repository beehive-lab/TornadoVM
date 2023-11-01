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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.opencl.OCLExecutionEnvironment;

public class SPIRVOCLContext extends SPIRVContext {

    private OCLExecutionEnvironment context;
    private List<SPIRVOCLDeviceContext> spirvoclDeviceContext;

    public SPIRVOCLContext(SPIRVPlatform platform, List<SPIRVDevice> devices, OCLExecutionEnvironment context) {
        super(platform, devices);
        this.context = context;

        // Create a command queue per device;
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            context.createCommandQueue(deviceIndex);
        }

        spirvoclDeviceContext = new ArrayList<>();
        for (SPIRVDevice device : devices) {
            // We do not need command queue from this class, it was already created in the
            // constructor
            SPIRVOCLDeviceContext deviceContext = new SPIRVOCLDeviceContext(device, null, context);
            device.setDeviceContext(deviceContext);
            spirvoclDeviceContext.add(deviceContext);
        }
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvoclDeviceContext.get(deviceIndex);
    }

    @Override
    public SPIRVCommandQueue createCommandQueue(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public SPIRVCommandQueue getCommandQueueForDevice(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void freeMemory(long buffer, int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransferp) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] value, long hostOffset, int[] waitEvent, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents, ProfilerTransfer profilerTransfer) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void enqueueBarrier(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void flush(int deviceIndex) {
        throw new RuntimeException("Unimplemented");
    }
}
