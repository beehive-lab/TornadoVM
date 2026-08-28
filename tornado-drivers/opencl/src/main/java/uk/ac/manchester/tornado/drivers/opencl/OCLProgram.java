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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramBuildInfo.CL_PROGRAM_BUILD_LOG;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramBuildInfo.CL_PROGRAM_BUILD_STATUS;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_BINARY_SIZES;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_DEVICES;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLProgramInfo.CL_PROGRAM_NUM_DEVICES;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLProgram {

    private final long programPointer;
    private final OCLDeviceContext deviceContext;
    private final long[] devices;
    private final List<OCLKernel> kernels;
    private final ByteBuffer buffer;
    private final TornadoLogger logger;

    public OCLProgram(long oclProgramPointer, OCLDeviceContext deviceContext) {
        this.programPointer = oclProgramPointer;
        this.deviceContext = deviceContext;
        this.devices = new long[] { deviceContext.getDeviceId() };
        this.kernels = new ArrayList<>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    /** {@code CL_PROGRAM_BINARIES}; the sizes query already has an enum constant. */
    private static final int CL_PROGRAM_BINARIES = 0x1166;

    /** Reusable per-thread native buffer the driver answers info queries into. */
    private static final FFMSupport.Staging INFO_STAGING = new FFMSupport.Staging();

    static void clReleaseProgram(long programId) throws OCLException {
        OpenCLAPI.clReleaseProgram(programId);
    }

    static void clBuildProgram(long programId, long[] devices, String options) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment deviceIds = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, Math.max(devices.length, 1));
            for (int i = 0; i < devices.length; i++) {
                deviceIds.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), MemorySegment.ofAddress(devices[i]));
            }
            MemorySegment buildOptions = options == null ? MemorySegment.NULL : FFMSupport.allocateCString(arena, options);
            // The build status is not raised here: the caller reads it back with
            // clGetProgramBuildInfo, which is also where the compiler log comes from.
            OpenCLAPI.clBuildProgram(programId, devices.length, deviceIds, buildOptions, MemorySegment.NULL, MemorySegment.NULL);
        }
    }

    static void clGetProgramInfo(long programId, int param, byte[] buffer) throws OCLException {
        Arrays.fill(buffer, (byte) 0);
        MemorySegment value = INFO_STAGING.forBytes(buffer.length);
        if (OpenCLAPI.clGetProgramInfo(programId, param, buffer.length, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
            return;
        }
        MemorySegment.copy(value, FFMSupport.C_CHAR, 0, buffer, 0, buffer.length);
    }

    static void clGetProgramBuildInfo(long programId, long deviceId, int param, byte[] buffer) throws OCLException {
        Arrays.fill(buffer, (byte) 0);
        MemorySegment value = INFO_STAGING.forBytes(buffer.length);
        if (OpenCLAPI.clGetProgramBuildInfo(programId, deviceId, param, buffer.length, value, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
            return;
        }
        MemorySegment.copy(value, FFMSupport.C_CHAR, 0, buffer, 0, buffer.length);
    }

    static long clCreateKernel(long programId, String name) throws OCLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = FFMSupport.allocateInt(arena);
            long kernel = OpenCLAPI.clCreateKernel(programId, FFMSupport.allocateCString(arena, name), status);
            int result = status.get(FFMSupport.C_INT, 0);
            if (result != OpenCLAPI.CL_SUCCESS) {
                throw new OCLException("clCreateKernel(" + name + ") failed: CL error " + result);
            }
            return kernel;
        }
    }

    /**
     * Copies each device's compiled binary into the provided direct buffer, back to back.
     *
     * <p>
     * {@code CL_PROGRAM_BINARIES} takes an array of destination pointers, one per device, which the
     * driver writes through; the sizes query first says how much each will take, so the pointers can
     * be laid out end to end.
     */
    static void getBinaries(long programId, long numDevices, ByteBuffer buffer) throws OCLException {
        int devices = (int) numDevices;
        if (devices <= 0) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizes = FFMSupport.allocateArray(arena, FFMSupport.C_LONG, devices);
            if (OpenCLAPI.clGetProgramInfo(programId, CL_PROGRAM_BINARY_SIZES.getValue(), FFMSupport.C_LONG.byteSize() * devices, sizes, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
                return;
            }
            long total = 0;
            for (int i = 0; i < devices; i++) {
                total += sizes.get(FFMSupport.C_LONG, i * FFMSupport.C_LONG.byteSize());
            }
            if (total <= 0) {
                return;
            }
            MemorySegment staging = arena.allocate(total, 8);
            MemorySegment binaries = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, devices);
            long offset = 0;
            for (int i = 0; i < devices; i++) {
                binaries.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), staging.asSlice(offset));
                offset += sizes.get(FFMSupport.C_LONG, i * FFMSupport.C_LONG.byteSize());
            }
            if (OpenCLAPI.clGetProgramInfo(programId, CL_PROGRAM_BINARIES, FFMSupport.C_POINTER.byteSize() * devices, binaries, MemorySegment.NULL) != OpenCLAPI.CL_SUCCESS) {
                return;
            }
            int length = (int) Math.min(total, buffer.remaining());
            byte[] bytes = new byte[length];
            MemorySegment.copy(staging, FFMSupport.C_CHAR, 0, bytes, 0, length);
            buffer.put(bytes);
        }
    }

    public OCLBuildStatus getStatus(long deviceId) {
        OCLBuildStatus result;
        buffer.clear();
        try {
            clGetProgramBuildInfo(programPointer, deviceId, CL_PROGRAM_BUILD_STATUS.getValue(), buffer.array());
            result = OCLBuildStatus.toEnum(buffer.getInt());
        } catch (OCLException e) {
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
        } catch (OCLException | UnsupportedEncodingException e) {
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
        } catch (OCLException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            kernels.forEach(OCLKernel::cleanup);
            clReleaseProgram(programPointer);
        } catch (OCLException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_NUM_DEVICES.getValue(), buffer.array());
            result = buffer.getInt();
        } catch (OCLException e) {
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
        } catch (OCLException e) {
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
        } catch (OCLException e) {
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

        } catch (OCLException e) {
            logger.error("unable to retrieve binary from OpenCL driver: %s", e.getMessage());
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

    public OCLKernel clCreateKernel(String entryPoint) {
        OCLKernel kernel;
        try {
            kernel = new OCLKernel(clCreateKernel(programPointer, entryPoint), deviceContext);
            kernels.add(kernel);
        } catch (OCLException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        new TornadoLogger().debug("Num devices: %d", numDevices);
    }

}
