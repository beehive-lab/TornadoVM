/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProgramBuildInfo.CL_PROGRAM_BUILD_LOG;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProgramBuildInfo.CL_PROGRAM_BUILD_STATUS;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProgramInfo.CL_PROGRAM_BINARY_SIZES;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProgramInfo.CL_PROGRAM_DEVICES;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalProgramInfo.CL_PROGRAM_NUM_DEVICES;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalBuildStatus;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class MetalProgram {

    private final long programPointer;
    private final MetalDeviceContext deviceContext;
    private final long[] devices;
    private final List<MetalKernel> kernels;
    private final ByteBuffer buffer;
    private final TornadoLogger logger;

    public MetalProgram(long oclProgramPointer, MetalDeviceContext deviceContext) {
        this.programPointer = oclProgramPointer;
        this.deviceContext = deviceContext;
        this.devices = new long[] { deviceContext.getDeviceId() };
        this.kernels = new ArrayList<>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(Metal.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    static native void clReleaseProgram(long programId) throws MetalException;

    static native void clBuildProgram(long programId, long[] devices, String options) throws MetalException;

    static native void clGetProgramInfo(long programId, int param, byte[] buffer) throws MetalException;

    static native void clGetProgramBuildInfo(long programId, long deviceId, int param, byte[] buffer) throws MetalException;

    static native long clCreateKernel(long programId, String name) throws MetalException;

    static native void getBinaries(long programId, long numDevices, ByteBuffer buffer) throws MetalException;

    public MetalBuildStatus getStatus(long deviceId) {
        MetalBuildStatus result;
        buffer.clear();
        try {
            clGetProgramBuildInfo(programPointer, deviceId, CL_PROGRAM_BUILD_STATUS.getValue(), buffer.array());
            result = MetalBuildStatus.toEnum(buffer.getInt());
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public String getBuildLog(long deviceId) {
        String result = "";
        buffer.clear();
        try {
            clGetProgramBuildInfo(programPointer, deviceId, CL_PROGRAM_BUILD_LOG.getValue(), buffer.array());
            result = new String(buffer.array(), "ASCII");
        } catch (MetalException | UnsupportedEncodingException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        result = result.substring(0, result.indexOf('\0'));
        return result;
    }

    public void build(String options) {
        buffer.clear();
        try {
            clBuildProgram(programPointer, devices, options);
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            kernels.forEach(MetalKernel::cleanup);
            clReleaseProgram(programPointer);
        } catch (MetalException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_NUM_DEVICES.getValue(), buffer.array());
            result = buffer.getInt();
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long[] getDevices() {
        final int numDevices = getNumDevices();
        long result[] = new long[numDevices];
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_DEVICES.getValue(), buffer.array());
            for (int i = 0; i < numDevices; i++) {
                result[i] = buffer.getLong();
            }
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public long[] getBinarySizes() {
        final int numDevices = getNumDevices();
        long result[] = new long[numDevices];
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_BINARY_SIZES.getValue(), buffer.array());
            for (int i = 0; i < numDevices; i++) {
                result[i] = buffer.getLong();
            }
        } catch (MetalException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    public void dumpBinaries(String filenamePrefix) {

        final long[] devices = getDevices();
        final int numDevices = getNumDevices();
        final long[] sizes = getBinarySizes();

        int index = 0;
        int offset = 0;
        for (; index < devices.length; index++) {
            if (devices[index] == deviceContext.getDeviceId()) {
                break;
            }
            offset += sizes[index];
        }

        int totalSize = 0;
        for (long size : sizes) {
            totalSize += (int) size;
        }

        final ByteBuffer binary = ByteBuffer.allocateDirect(totalSize);
        try {
            getBinaries(programPointer, numDevices, binary);

            logger.info("dumping binary %s", filenamePrefix);
            try (FileOutputStream fis = new FileOutputStream(filenamePrefix); FileChannel vChannel = fis.getChannel();) {
                binary.position(offset);
                binary.limit(offset + (int) sizes[index]);
                vChannel.write(binary);
            } catch (IOException e) {
                logger.error("unable to dump binary: %s", e.getMessage());
            }

        } catch (MetalException e) {
            logger.error("unable to retrieve binary from Metal driver: %s", e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("program: id=0x%x, num devices=%d\n", programPointer, devices.length));
        for (long device : devices) {
            sb.append(String.format("device: id=0x%x, status=%s\n", device, getStatus(device)));
        }

        return sb.toString();
    }

    public MetalKernel clCreateKernel(String entryPoint) {
        MetalKernel kernel;
        try {
            kernel = new MetalKernel(clCreateKernel(programPointer, entryPoint), deviceContext);
            kernels.add(kernel);
        } catch (MetalException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        new TornadoLogger().debug("Num devices: %d", numDevices);
    }

}
