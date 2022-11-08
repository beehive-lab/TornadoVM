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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_UNKNOWN;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramBuildInfo.CL_PROGRAM_BUILD_LOG;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramBuildInfo.CL_PROGRAM_BUILD_STATUS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_BINARY_SIZES;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_DEVICES;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_NUM_DEVICES;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLProgram extends TornadoLogger {

    private final long id;
    private final OCLDeviceContext deviceContext;
    private final long[] devices;
    private final List<OCLKernel> kernels;
    private final ByteBuffer buffer;

    public OCLProgram(long id, OCLDeviceContext deviceContext) {
        this.id = id;
        this.deviceContext = deviceContext;
        this.devices = new long[] { deviceContext.getDeviceId() };
        this.kernels = new ArrayList<>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(OpenCL.BYTE_ORDER);
    }

    static native void clReleaseProgram(long programId) throws OCLException;

    static native void clBuildProgram(long programId, long[] devices, String options) throws OCLException;

    static native void clGetProgramInfo(long programId, int param, byte[] buffer) throws OCLException;

    static native void clGetProgramBuildInfo(long programId, long deviceId, int param, byte[] buffer) throws OCLException;

    static native long clCreateKernel(long programId, String name) throws OCLException;

    static native void getBinaries(long programId, long numDevices, ByteBuffer buffer) throws OCLException;

    public OCLBuildStatus getStatus(long deviceId) {
        OCLBuildStatus result = CL_BUILD_UNKNOWN;
        buffer.clear();
        try {
            clGetProgramBuildInfo(id, deviceId, CL_PROGRAM_BUILD_STATUS.getValue(), buffer.array());
            result = OCLBuildStatus.toEnum(buffer.getInt());
        } catch (OCLException e) {
            error(e.getMessage());
        }
        return result;
    }

    public String getBuildLog(long deviceId) {
        String result = "";
        buffer.clear();
        try {
            clGetProgramBuildInfo(id, deviceId, CL_PROGRAM_BUILD_LOG.getValue(), buffer.array());

            result = new String(buffer.array(), "ASCII");
        } catch (OCLException | UnsupportedEncodingException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        result = result.substring(0, result.indexOf('\0'));
        return result;
    }

    public void build(String options) {

        buffer.clear();

        try {
            clBuildProgram(id, devices, options);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            for (OCLKernel kernel : kernels) {
                kernel.cleanup();
            }

            clReleaseProgram(id);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(id, CL_PROGRAM_NUM_DEVICES.getValue(), buffer.array());
            result = buffer.getInt();
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public long[] getDevices() {
        final int numDevices = getNumDevices();
        long result[] = new long[numDevices];
        buffer.clear();
        try {
            clGetProgramInfo(id, CL_PROGRAM_DEVICES.getValue(), buffer.array());
            for (int i = 0; i < numDevices; i++) {
                result[i] = buffer.getLong();
            }
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public long[] getBinarySizes() {
        final int numDevices = getNumDevices();
        long result[] = new long[numDevices];
        buffer.clear();
        try {
            clGetProgramInfo(id, CL_PROGRAM_BINARY_SIZES.getValue(), buffer.array());
            for (int i = 0; i < numDevices; i++) {
                result[i] = buffer.getLong();
            }
        } catch (OCLException e) {
            error(e.getMessage());
            e.printStackTrace();
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
            getBinaries(id, numDevices, binary);

            info("dumping binary %s", filenamePrefix);
            try (FileOutputStream fis = new FileOutputStream(filenamePrefix); FileChannel vChannel = fis.getChannel();) {
                binary.position(offset);
                binary.limit(offset + (int) sizes[index]);
                vChannel.write(binary);
            } catch (IOException e) {
                error("unable to dump binary: %s", e.getMessage());
            }

        } catch (OCLException e) {
            error("unable to retrieve binary from OpenCL driver: %s", e.getMessage());
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("program: id=0x%x, num devices=%d\n", id, devices.length));
        for (long device : devices) {
            sb.append(String.format("device: id=0x%x, status=%s\n", device, getStatus(device)));
        }

        return sb.toString();
    }

    public OCLKernel getKernel(String entryPoint) {
        OCLKernel kernel = null;
        try {
            kernel = new OCLKernel(clCreateKernel(id, entryPoint), deviceContext);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        debug("Num devices: %d", numDevices);
    }

}
