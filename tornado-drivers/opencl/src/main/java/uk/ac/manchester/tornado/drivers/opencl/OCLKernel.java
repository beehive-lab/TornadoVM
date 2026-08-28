/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLKernelInfo;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLKernel {

    private final long oclKernelID;
    private final OCLDeviceContext deviceContext;
    private final ByteBuffer buffer;
    private String kernelName;
    private final TornadoLogger logger;

    public OCLKernel(long id, OCLDeviceContext deviceContext) {
        this.oclKernelID = id;
        this.deviceContext = deviceContext;
        this.buffer = ByteBuffer.allocate(1024);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.kernelName = "unknown";
        this.logger = new TornadoLogger(this.getClass());
        queryName();

    }

    /** Reusable per-thread native buffers for the argument value and for info queries. */
    private static final FFMSupport.Staging ARG_STAGING = new FFMSupport.Staging();

    private static final FFMSupport.Staging INFO_STAGING = new FFMSupport.Staging();

    static void clReleaseKernel(long kernelId) throws OCLException {
        OpenCLAPI.clReleaseKernel(kernelId);
    }

    /**
     * Sets an argument. A null {@code buffer} means a {@code __local} argument of the given size,
     * which OpenCL expresses as a null value pointer.
     */
    static void clSetKernelArg(long kernelId, int index, long size, byte[] buffer) throws OCLException {
        MemorySegment value = MemorySegment.NULL;
        if (buffer != null) {
            value = ARG_STAGING.forBytes(buffer.length);
            MemorySegment.copy(buffer, 0, value, FFMSupport.C_CHAR, 0, buffer.length);
        }
        OpenCLAPI.clSetKernelArg(kernelId, index, size, value);
    }

    /**
     * Sets a {@code cl_mem} argument by value.
     *
     * <p>
     * The JNI build declared this but never implemented it, so {@link #setArgRef} threw
     * {@code UnsatisfiedLinkError} if it was ever reached. It has a working implementation here.
     */
    static void clSetKernelArgRef(long kernelId, int index, long buffer) throws OCLException {
        MemorySegment value = ARG_STAGING.forBytes(Long.BYTES);
        value.set(FFMSupport.C_LONG, 0, buffer);
        OpenCLAPI.clSetKernelArg(kernelId, index, Long.BYTES, value);
    }

    static void clGetKernelInfo(long kernelId, int info, byte[] buffer) throws OCLException {
        Arrays.fill(buffer, (byte) 0);
        MemorySegment value = INFO_STAGING.forBytes(buffer.length);
        if (OpenCLAPI.clGetKernelInfo(kernelId, info, buffer.length, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
            return;
        }
        MemorySegment.copy(value, FFMSupport.C_CHAR, 0, buffer, 0, buffer.length);
    }

    public void setArg(int index, ByteBuffer buffer) {
        try {
            clSetKernelArg(oclKernelID, index, buffer.position(), buffer.array());
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgRef(int index, long devicePtr) {
        System.out.println("Calling the new function");
        try {
            clSetKernelArgRef(oclKernelID, index, devicePtr);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgUnused(int index) {
        try {
            clSetKernelArg(oclKernelID, index, 8, null);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    public void setConstantRegion(int index, ByteBuffer buffer) {
        long maxSize = deviceContext.getDevice().getDeviceMaxConstantBufferSize();
        guarantee(buffer.position() <= maxSize, "constant buffer is too large for device");
        setArg(index, buffer);
    }

    public void setLocalRegion(int index, long size) {
        long maxSize = deviceContext.getDevice().getDeviceLocalMemorySize();
        guarantee(size <= maxSize, "local allocation is too large for device");
        try {
            clSetKernelArg(oclKernelID, index, size, null);
        } catch (OCLException e) {
            logger.error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            clReleaseKernel(oclKernelID);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return kernelName;
    }

    private void queryName() {
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.clear();
        try {
            clGetKernelInfo(oclKernelID, OCLKernelInfo.CL_KERNEL_FUNCTION_NAME.getValue(), buffer.array());
            kernelName = new String(buffer.array(), StandardCharsets.US_ASCII);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public long getOclKernelID() {
        return oclKernelID;
    }
}
