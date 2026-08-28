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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProgramBuildInfo.CL_PROGRAM_BUILD_LOG;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProgramBuildInfo.CL_PROGRAM_BUILD_STATUS;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProgramInfo.CL_PROGRAM_BINARY_SIZES;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProgramInfo.CL_PROGRAM_DEVICES;
import static uk.ac.manchester.tornado.drivers.cuda.enums.CUDAProgramInfo.CL_PROGRAM_NUM_DEVICES;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDABuildStatus;
import uk.ac.manchester.tornado.drivers.cuda.exceptions.CUDAException;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDACompiler;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDAHandles;
import uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAProgram {

    private final long programPointer;
    private final CUDADeviceContext deviceContext;
    private final long[] devices;
    private final List<CUDAKernel> kernels;
    private final ByteBuffer buffer;
    private final TornadoLogger logger;

    public CUDAProgram(long oclProgramPointer, CUDADeviceContext deviceContext) {
        this.programPointer = oclProgramPointer;
        this.deviceContext = deviceContext;
        this.devices = new long[] { deviceContext.getDeviceId() };
        this.kernels = new ArrayList<>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(CUDADriver.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    static void clReleaseProgram(long programId) throws CUDAException {
        CUDAHandles.Program program = (CUDAHandles.Program) CUDAHandles.release(programId);
        if (program != null && program.moduleLoaded) {
            CUDADriverAPI.cuModuleUnload(program.module);
        }
    }

    /**
     * Compiles the program's CUDA C for the first device's architecture with NVRTC and loads the
     * resulting module. The OpenCL clone builds for a set of devices; CUDA modules are per-context,
     * so the first device is the one that decides the target architecture.
     */
    static void clBuildProgram(long programId, long[] devices, String options) throws CUDAException {
        CUDAHandles.Program program = CUDAHandles.resolve(programId, CUDAHandles.Program.class);
        if (program == null) {
            return;
        }
        int device = 0;
        if (devices.length > 0) {
            CUDAHandles.Device boxed = CUDAHandles.resolve(devices[0], CUDAHandles.Device.class);
            if (boxed != null) {
                device = boxed.device();
            }
        }
        CUDACompiler.build(program, device, options);
    }

    static void clGetProgramInfo(long programId, int param, byte[] buffer) throws CUDAException {
        Arrays.fill(buffer, (byte) 0);
        CUDAHandles.Program program = CUDAHandles.resolve(programId, CUDAHandles.Program.class);
        ByteBuffer out = ByteBuffer.wrap(buffer).order(CUDADriver.BYTE_ORDER);
        if (param == CL_PROGRAM_NUM_DEVICES.getValue() && buffer.length >= Integer.BYTES) {
            out.putInt(1);
        } else if (param == CL_PROGRAM_BINARY_SIZES.getValue() && buffer.length >= Long.BYTES) {
            out.putLong(program == null ? 0 : program.binary.length);
        }
    }

    /** Surfaces the NVRTC build status and log to the Java side. */
    static void clGetProgramBuildInfo(long programId, long deviceId, int param, byte[] buffer) throws CUDAException {
        Arrays.fill(buffer, (byte) 0);
        CUDAHandles.Program program = CUDAHandles.resolve(programId, CUDAHandles.Program.class);
        if (program == null) {
            return;
        }
        if (param == CL_PROGRAM_BUILD_STATUS.getValue() && buffer.length >= Integer.BYTES) {
            ByteBuffer.wrap(buffer).order(CUDADriver.BYTE_ORDER).putInt(program.buildStatus);
        } else if (param == CL_PROGRAM_BUILD_LOG.getValue()) {
            byte[] log = program.log.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(log.length, buffer.length - 1);
            System.arraycopy(log, 0, buffer, 0, length);
            buffer[length] = 0; // getBuildLog substrings up to the first NUL
        }
    }

    /** Resolves a {@code CUfunction} from the loaded module. */
    static long clCreateKernel(long programId, String name) throws CUDAException {
        CUDAHandles.Program program = CUDAHandles.resolve(programId, CUDAHandles.Program.class);
        if (program == null || !program.moduleLoaded) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment function = FFMSupport.allocatePointer(arena);
            int result = CUDADriverAPI.cuModuleGetFunction(function, program.module, FFMSupport.allocateCString(arena, name));
            if (result != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            long functionPointer = function.get(FFMSupport.C_POINTER, 0).address();
            return CUDAHandles.register(new CUDAHandles.Kernel(functionPointer, program.module, name));
        }
    }

    /** Copies the compiled module image (cubin or PTX) into the provided direct buffer. */
    static void getBinaries(long programId, long numDevices, ByteBuffer buffer) throws CUDAException {
        CUDAHandles.Program program = CUDAHandles.resolve(programId, CUDAHandles.Program.class);
        if (program == null) {
            return;
        }
        int length = Math.min(program.binary.length, buffer.remaining());
        if (length > 0) {
            buffer.put(program.binary, 0, length);
        }
    }

    private static Boolean nativeFP8ConversionAvailable;

    private static Integer nvrtcVersionCached;

    /**
     * NVRTC version as {@code major * 1000 + minor} (CUDA 12.4 gives 12004), or {@code -1} when the
     * query fails. Queried once per process.
     *
     * <p>This gates features whose PTX encoding needs a minimum PTX ISA version: NVRTC stamps the
     * PTX it emits with its own {@code .version}, so an instruction introduced in a later ISA
     * cannot be expressed by an older toolkit, however capable the GPU is. FP8 {@code mma.sync}
     * (PTX ISA 8.4 / CUDA 12.4) is the motivating case - on an older toolkit ptxas rejects it with
     * "Feature 'mma with FP8 floating point type' requires PTX ISA .version 8.4 or later".</p>
     */
    public static synchronized int getNvrtcVersion() {
        if (nvrtcVersionCached == null) {
            nvrtcVersionCached = CUDACompiler.version();
        }
        return nvrtcVersionCached;
    }

    /**
     * Whether this NVRTC/toolkit pair can compile kernels that include {@code cuda_fp8.h}
     * (absent before CUDA 11.8). Probed once per process: the FP8 conversion plugins consult
     * this to choose between emitting the native {@code __nv_cvt} intrinsics and inlining the
     * Java software decoder, so an old or mismatched toolkit degrades FP8 decode performance,
     * not correctness.
     */
    public static synchronized boolean isNativeFP8ConversionAvailable() {
        if (nativeFP8ConversionAvailable == null) {
            nativeFP8ConversionAvailable = CUDACompiler.canCompileHeader("cuda_fp8.h");
        }
        return nativeFP8ConversionAvailable;
    }

    public CUDABuildStatus getStatus(long deviceId) {
        CUDABuildStatus result;
        buffer.clear();
        try {
            clGetProgramBuildInfo(programPointer, deviceId, CL_PROGRAM_BUILD_STATUS.getValue(), buffer.array());
            result = CUDABuildStatus.toEnum(buffer.getInt());
        } catch (CUDAException e) {
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
        } catch (CUDAException | UnsupportedEncodingException e) {
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
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            kernels.forEach(CUDAKernel::cleanup);
            clReleaseProgram(programPointer);
        } catch (CUDAException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_NUM_DEVICES.getValue(), buffer.array());
            result = buffer.getInt();
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
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
        } catch (CUDAException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    /**
     * Returns the compiled module image (cubin, or PTX when the toolkit cannot emit cubin for this
     * device) for the device this program was built for, or null when no image is available.
     */
    public byte[] getModuleImage() {
        // A CUDA program holds exactly one module image, for the device it was built for. (Note that
        // the device-matching loop in dumpBinaries() cannot work here: the driver does not implement
        // CL_PROGRAM_DEVICES, so getDevices() returns zeros.)
        final long[] sizes = getBinarySizes();
        if (sizes.length == 0 || sizes[0] <= 0) {
            return null;
        }

        try {
            final int size = (int) sizes[0];
            final ByteBuffer binary = ByteBuffer.allocateDirect(size);
            getBinaries(programPointer, getNumDevices(), binary);
            final byte[] image = new byte[size];
            binary.position(0);
            binary.get(image);
            return image;
        } catch (CUDAException e) {
            logger.error("unable to retrieve module image: %s", e.getMessage());
            return null;
        }
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

        } catch (CUDAException e) {
            logger.error("unable to retrieve binary from CUDADriver driver: %s", e.getMessage());
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

    public CUDAKernel clCreateKernel(String entryPoint) {
        CUDAKernel kernel;
        try {
            kernel = new CUDAKernel(clCreateKernel(programPointer, entryPoint), deviceContext);
            kernels.add(kernel);
        } catch (CUDAException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        new TornadoLogger().debug("Num devices: %d", numDevices);
    }

}
