/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
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
