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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAKernelInfo;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAKernel {

    private final long oclKernelID;
    private final CUDADeviceContext deviceContext;
    private final ByteBuffer buffer;
    private String kernelName;
    private final TornadoLogger logger;

    public CUDAKernel(long id, CUDADeviceContext deviceContext) {
        this.oclKernelID = id;
        this.deviceContext = deviceContext;
        this.buffer = ByteBuffer.allocate(1024);
        this.buffer.order(CUDADriver.BYTE_ORDER);
        this.kernelName = "unknown";
        this.logger = new TornadoLogger(this.getClass());
        queryName();

    }

    native static void clReleaseKernel(long kernelId) throws CUDAException;

    native static void clSetKernelArg(long kernelId, int index, long size, byte[] buffer) throws CUDAException;

    native static void clSetKernelArgRef(long kernelId, int index, long buffer) throws CUDAException;

    native static void clGetKernelInfo(long kernelId, int info, byte[] buffer) throws CUDAException;

    native static int cuOccupancyMaxPotentialBlockSize(long kernelId) throws CUDAException;

    private int maxPotentialBlockSize = -1;

    /**
     * CUDA occupancy-suggested maximum threads per block for this kernel, accounting for its
     * register and shared-memory usage (via {@code cuOccupancyMaxPotentialBlockSize}). Cached.
     * Returns 0 if the query fails, in which case callers should fall back to their heuristic.
     */
    public int getMaxPotentialBlockSize() {
        if (maxPotentialBlockSize < 0) {
            try {
                maxPotentialBlockSize = cuOccupancyMaxPotentialBlockSize(oclKernelID);
            } catch (CUDAException e) {
                logger.error(e.getMessage());
                maxPotentialBlockSize = 0;
            }
        }
        return maxPotentialBlockSize;
    }

    public void setArg(int index, ByteBuffer buffer) {
        try {
            clSetKernelArg(oclKernelID, index, buffer.position(), buffer.array());
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgRef(int index, long devicePtr) {
        System.out.println("Calling the new function");
        try {
            clSetKernelArgRef(oclKernelID, index, devicePtr);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    public void setArgUnused(int index) {
        try {
            clSetKernelArg(oclKernelID, index, 8, null);
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            clReleaseKernel(oclKernelID);
        } catch (CUDAException e) {
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
            clGetKernelInfo(oclKernelID, CUDAKernelInfo.CL_KERNEL_FUNCTION_NAME.getValue(), buffer.array());
            kernelName = new String(buffer.array(), StandardCharsets.US_ASCII);
        } catch (CUDAException e) {
            e.printStackTrace();
        }
    }

    public long getOclKernelID() {
        return oclKernelID;
    }
}
