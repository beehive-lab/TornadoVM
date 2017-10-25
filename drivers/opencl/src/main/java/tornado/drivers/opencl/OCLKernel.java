/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLKernelInfo;
import tornado.drivers.opencl.exceptions.OCLException;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

public class OCLKernel extends TornadoLogger {

    private final long id;
    private final OCLDeviceContext deviceContext;
    private final ByteBuffer buffer;
    private String kernelName;

    public OCLKernel(long id, OCLDeviceContext deviceContext) {
        this.id = id;
        this.deviceContext = deviceContext;
        this.buffer = ByteBuffer.allocate(1024);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.kernelName = "unknown";

        queryName();

    }

    native static void clReleaseKernel(long kernelId) throws OCLException;

    native static void clSetKernelArg(long kernelId, int index, long size,
            byte[] buffer) throws OCLException;

    native static void clGetKernelInfo(long kernelId, int info, byte[] buffer) throws OCLException;

    public void setArg(int index, ByteBuffer buffer) {
        try {
            clSetKernelArg(id, index, buffer.position(), buffer.array());
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void setArgUnused(int index) {
        try {
            clSetKernelArg(id, index, 8, null);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void setConstantRegion(int index, ByteBuffer buffer) {
        long maxSize = deviceContext.getDevice().getMaxConstantBufferSize();
        guarantee(buffer.position() <= maxSize, "constant buffer is too large for device");
        setArg(index, buffer);
    }

    public void setLocalRegion(int index, long size) {
        long maxSize = deviceContext.getDevice().getLocalMemorySize();
        guarantee(size <= maxSize, "local allocation is too large for device");
        try {
            clSetKernelArg(id, index, size, null);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            clReleaseKernel(id);
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
            clGetKernelInfo(id, OCLKernelInfo.CL_KERNEL_FUNCTION_NAME.getValue(),
                    buffer.array());
            kernelName = new String(buffer.array(), "ASCII").trim();
        } catch (UnsupportedEncodingException | OCLException e) {
            e.printStackTrace();
        }
    }

    public long getId() {
        return id;
    }
}
