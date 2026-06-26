/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoNoOpenCLPlatformException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDACommandQueueProperties;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class CUDAContext implements CUDAContextInterface {

    private final long contextID;
    private final List<CUDATargetDevice> devices;
    private final List<CUDADeviceContext> deviceContexts;

    private final CUDAPlatform platform;

    private final TornadoLogger logger;

    public CUDAContext(CUDAPlatform platform, long contextPointer, List<CUDATargetDevice> devices) {
        this.platform = platform;
        this.contextID = contextPointer;
        this.devices = devices;
        this.deviceContexts = new ArrayList<>(devices.size());
        this.logger = new TornadoLogger(this.getClass());
    }

    native void clReleaseContext(long id) throws CUDAException;

    native void clGetContextInfo(long id, int info, byte[] buffer) throws CUDAException;

    public native long clCreateCommandQueue(long contextId, long deviceId, long properties) throws CUDAException;

    native long allocateOffHeapMemory(long size, long alignment);

    native void freeOffHeapMemory(long address);

    native ByteBuffer asByteBuffer(long address, long size);

    // creates an empty buffer on the device
    native CUDABufferResult createBuffer(long contextId, long flags, long size, long hostPointer) throws CUDAException;

    // zero-initialises a device buffer (cuMemsetD8 to 0)
    native int memSetZero(long contextId, long devicePointer, long bytes);

    native long createSubBuffer(long buffer, long flags, int createType, byte[] createInfo) throws CUDAException;

    native void clReleaseMemObject(long memId) throws CUDAException;

    native long clCreateProgramWithSource(long contextId, byte[] data, long[] lengths) throws CUDAException;

    native long clCreateProgramWithBinary(long contextId, long deviceId, byte[] data, long[] lengths) throws CUDAException;

    native long clCreateProgramWithIL(long contextId, byte[] spirvBinaryCode, long[] lengths) throws CUDAException;

    public int getNumDevices() {
        return devices.size();
    }

    public List<CUDATargetDevice> devices() {
        return devices;
    }

    @Override
    public long getContextId() {
        return contextID;
    }

    private void createCommandQueue(int index, long properties) {
        CUDATargetDevice device = devices.get(index);
        try {

            final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
            final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;

            logger.info("platform: version=%s (%s) on %s", platformVersion, platform.getVersion(), device.getDeviceName());
            logger.info("device  : version=%s (%s) on %s", deviceVersion, device.getVersion(), device.getDeviceName());

            clCreateCommandQueue(contextID, device.getDevicePointer(), properties);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException("[ERROR] CUDADriver Command Queue Initialization not valid");
        }
    }

    public long getProperties() {
        long properties = 0;
        if (TornadoOptions.ENABLE_OPENCL_PROFILING) {
            properties |= CUDACommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
        }

        if (TornadoOptions.ENABLE_OOO_EXECUTION) {
            properties |= CUDACommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        return properties;
    }

    @Override
    public void createCommandQueue(int index) {
        long properties = getProperties();
        createCommandQueue(index, properties);
    }

    public CUDAProgram createProgramWithSource(byte[] source, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program = null;

        try {
            program = new CUDAProgram(clCreateProgramWithSource(contextID, source, lengths), deviceContext);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public CUDAProgram createProgramWithIL(byte[] spirvBinary, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program;
        try {
            long programID = clCreateProgramWithIL(contextID, spirvBinary, lengths);
            if (programID == -1) {
                throw new TornadoNoOpenCLPlatformException("CUDADriver version <= 2.1. clCreateProgramWithIL is not supported");
            }
            program = new CUDAProgram(programID, deviceContext);
        } catch (CUDAException e) {
            throw new TornadoRuntimeException(e);
        }

        return program;
    }

    public CUDAProgram createProgramWithBinary(long deviceId, byte[] binary, long[] lengths, CUDADeviceContext deviceContext) {
        CUDAProgram program = null;

        try {
            program = new CUDAProgram(clCreateProgramWithBinary(contextID, deviceId, binary, lengths), deviceContext);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }

        return program;
    }

    public void cleanup() {

        if (TornadoOptions.DUMP_EVENTS) {
            for (CUDADeviceContext deviceContext : deviceContexts) {
                deviceContext.dumpEvents();
            }
        }

        try {
            long t1 = System.nanoTime();
            clReleaseContext(contextID);
            long t2 = System.nanoTime();

            if (TornadoOptions.FULL_DEBUG) {
                System.out.printf("cleanup: %-10s..........%.9f s%n", "context", (t2 - t1) * 1e-9);
            }
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoRuntimeException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("id=0x%x, device count=%d", contextID, getNumDevices());
    }

    @Override
    public CUDADeviceContext createDeviceContext(int index) {
        logger.debug("creating device context for device: %s", devices.get(index).toString());
        createCommandQueue(index);
        final CUDADeviceContext deviceContext = new CUDADeviceContext(devices.get(index), this);
        deviceContexts.add(deviceContext);
        return deviceContext;
    }

    public CUDABufferResult createBuffer(long flags, long bytes) {
        return createBuffer(flags, bytes, 0L);
    }

    private CUDABufferResult createBuffer(long flags, long bytes, long hostPointer) {
        try {
            final CUDABufferResult result = createBuffer(contextID, flags, bytes, hostPointer);
            // cuMemAlloc reports failures (notably CUDA_ERROR_OUT_OF_MEMORY) via the
            // result status and a null device pointer rather than throwing. Surface it
            // as a clean exception here: otherwise the zero buffer is used by a later
            // copy/kernel launch and triggers an unrecoverable CUDA_ERROR_ILLEGAL_ADDRESS
            // that poisons the whole context.
            if (result == null || result.getResult() != CUDADriver.CUDA_SUCCESS || result.getBuffer() == 0L) {
                int status = (result == null) ? -1 : result.getResult();
                throw new TornadoOutOfMemoryException("[ERROR] Unable to allocate " + RuntimeUtilities.humanReadableByteCount(bytes, false) + " on the CUDA device (cuMemAlloc status=" + status
                        + ").\n\tThe allocation exceeds available device memory. Reduce the working set, or enable CUDA Unified Memory to over-subscribe VRAM.");
            }
            logger.info("buffer allocated %s @ 0x%x", RuntimeUtilities.humanReadableByteCount(bytes, false), result.getBuffer());
            return result;
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public void releaseBuffer(long bufferId) {
        try {
            clReleaseMemObject(bufferId);
            logger.info("buffer released 0x%x", bufferId);
        } catch (CUDAException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Zero-initialise a device buffer. Used to guarantee that (re)allocated
     * device buffers do not expose stale/garbage data when a kernel does not
     * write all (or any) of an output buffer.
     */
    public void zeroBuffer(long bufferId, long bytes) {
        if (bufferId == 0 || bytes <= 0) {
            return;
        }
        memSetZero(contextID, bufferId, bytes);
    }

    public int getPlatformIndex() {
        return platform.getIndex();
    }

    public CUDAPlatform getPlatform() {
        return platform;
    }

    public static class CUDABufferResult {

        private final long oclBuffer;
        private final long address;
        private final int result;

        /**
         * Objects of this type are created in Native Code from the JNI-CUDADriver layer of TornadoVM.
         */
        public CUDABufferResult(long oclBuffer, long address, int result) {
            this.oclBuffer = oclBuffer;
            this.address = address;
            this.result = result;
        }

        public long getBuffer() {
            return oclBuffer;
        }

        public long getAddress() {
            return address;
        }

        public int getResult() {
            return result;
        }
    }
}
