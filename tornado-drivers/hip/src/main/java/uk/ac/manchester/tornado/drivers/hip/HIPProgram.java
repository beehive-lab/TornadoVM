/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.hip;

import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProgramBuildInfo.CL_PROGRAM_BUILD_LOG;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProgramBuildInfo.CL_PROGRAM_BUILD_STATUS;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProgramInfo.CL_PROGRAM_BINARY_SIZES;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProgramInfo.CL_PROGRAM_DEVICES;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPProgramInfo.CL_PROGRAM_NUM_DEVICES;

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
import uk.ac.manchester.tornado.drivers.hip.enums.HIPBuildStatus;
import uk.ac.manchester.tornado.drivers.hip.exceptions.HIPException;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPCompiler;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPDriverAPI;
import uk.ac.manchester.tornado.drivers.hip.ffm.HIPHandles;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class HIPProgram {

    private final long programPointer;
    private final HIPDeviceContext deviceContext;
    private final long[] devices;
    private final List<HIPKernel> kernels;
    private final ByteBuffer buffer;
    private final TornadoLogger logger;

    public HIPProgram(long oclProgramPointer, HIPDeviceContext deviceContext) {
        this.programPointer = oclProgramPointer;
        this.deviceContext = deviceContext;
        this.devices = new long[] { deviceContext.getDeviceId() };
        this.kernels = new ArrayList<>();
        this.buffer = ByteBuffer.allocate(8192);
        this.buffer.order(HIPDriver.BYTE_ORDER);
        this.logger = new TornadoLogger(this.getClass());
    }

    static void clReleaseProgram(long programId) throws HIPException {
        HIPHandles.Program program = (HIPHandles.Program) HIPHandles.release(programId);
        if (program != null && program.moduleLoaded) {
            HIPDriverAPI.hipModuleUnload(program.module);
        }
    }

    /**
     * Compiles the program's HIP C++ for the first device's architecture with hipRTC and loads the
     * resulting module. The OpenCL clone builds for a set of devices; HIP modules are per-device,
     * so the first device is the one that decides the target architecture.
     */
    static void clBuildProgram(long programId, long[] devices, String options) throws HIPException {
        HIPHandles.Program program = HIPHandles.resolve(programId, HIPHandles.Program.class);
        if (program == null) {
            return;
        }
        int device = 0;
        if (devices.length > 0) {
            HIPHandles.Device boxed = HIPHandles.resolve(devices[0], HIPHandles.Device.class);
            if (boxed != null) {
                device = boxed.device();
            }
        }
        HIPCompiler.build(program, device, options);
    }

    static void clGetProgramInfo(long programId, int param, byte[] buffer) throws HIPException {
        Arrays.fill(buffer, (byte) 0);
        HIPHandles.Program program = HIPHandles.resolve(programId, HIPHandles.Program.class);
        ByteBuffer out = ByteBuffer.wrap(buffer).order(HIPDriver.BYTE_ORDER);
        if (param == CL_PROGRAM_NUM_DEVICES.getValue() && buffer.length >= Integer.BYTES) {
            out.putInt(1);
        } else if (param == CL_PROGRAM_BINARY_SIZES.getValue() && buffer.length >= Long.BYTES) {
            out.putLong(program == null ? 0 : program.binary.length);
        }
    }

    /** Surfaces the hipRTC build status and log to the Java side. */
    static void clGetProgramBuildInfo(long programId, long deviceId, int param, byte[] buffer) throws HIPException {
        Arrays.fill(buffer, (byte) 0);
        HIPHandles.Program program = HIPHandles.resolve(programId, HIPHandles.Program.class);
        if (program == null) {
            return;
        }
        if (param == CL_PROGRAM_BUILD_STATUS.getValue() && buffer.length >= Integer.BYTES) {
            ByteBuffer.wrap(buffer).order(HIPDriver.BYTE_ORDER).putInt(program.buildStatus);
        } else if (param == CL_PROGRAM_BUILD_LOG.getValue()) {
            byte[] log = program.log.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(log.length, buffer.length - 1);
            System.arraycopy(log, 0, buffer, 0, length);
            buffer[length] = 0; // getBuildLog substrings up to the first NUL
        }
    }

    /** Resolves a {@code hipFunction_t} from the loaded module. */
    static long clCreateKernel(long programId, String name) throws HIPException {
        HIPHandles.Program program = HIPHandles.resolve(programId, HIPHandles.Program.class);
        if (program == null || !program.moduleLoaded) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment function = FFMSupport.allocatePointer(arena);
            int result = HIPDriverAPI.hipModuleGetFunction(function, program.module, FFMSupport.allocateCString(arena, name));
            if (result != HIPDriverAPI.HIP_SUCCESS) {
                return 0;
            }
            long functionPointer = function.get(FFMSupport.C_POINTER, 0).address();
            return HIPHandles.register(new HIPHandles.Kernel(functionPointer, program.module, name));
        }
    }

    /** Copies the compiled module image (a code object) into the provided direct buffer. */
    static void getBinaries(long programId, long numDevices, ByteBuffer buffer) throws HIPException {
        HIPHandles.Program program = HIPHandles.resolve(programId, HIPHandles.Program.class);
        if (program == null) {
            return;
        }
        int length = Math.min(program.binary.length, buffer.remaining());
        if (length > 0) {
            buffer.put(program.binary, 0, length);
        }
    }

    private static Integer hiprtcVersionCached;

    /**
     * hipRTC version as {@code major * 1000 + minor} (HIP 7.2 gives 7002), or {@code -1} when the query
     * fails. Queried once per process; part of the on-disk code-object cache key.
     */
    public static synchronized int getHiprtcVersion() {
        if (hiprtcVersionCached == null) {
            hiprtcVersionCached = HIPCompiler.version();
        }
        return hiprtcVersionCached;
    }

    public HIPBuildStatus getStatus(long deviceId) {
        HIPBuildStatus result;
        buffer.clear();
        try {
            clGetProgramBuildInfo(programPointer, deviceId, CL_PROGRAM_BUILD_STATUS.getValue(), buffer.array());
            result = HIPBuildStatus.toEnum(buffer.getInt());
        } catch (HIPException e) {
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
        } catch (HIPException | UnsupportedEncodingException e) {
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
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public void cleanup() {
        try {
            kernels.forEach(HIPKernel::cleanup);
            clReleaseProgram(programPointer);
        } catch (HIPException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
    }

    public int getNumDevices() {
        int result = 0;
        buffer.clear();
        try {
            clGetProgramInfo(programPointer, CL_PROGRAM_NUM_DEVICES.getValue(), buffer.array());
            result = buffer.getInt();
        } catch (HIPException e) {
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
        } catch (HIPException e) {
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
        } catch (HIPException e) {
            logger.error(e.getMessage());
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }
        return result;
    }

    /**
     * Returns the compiled module image (a code object) for the device this program was built for, or
     * null when no image is available.
     */
    public byte[] getModuleImage() {
        // A HIP program holds exactly one module image, for the device it was built for. (Note that
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
        } catch (HIPException e) {
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

        } catch (HIPException e) {
            logger.error("unable to retrieve binary from HIPDriver driver: %s", e.getMessage());
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

    public HIPKernel clCreateKernel(String entryPoint) {
        HIPKernel kernel;
        try {
            kernel = new HIPKernel(clCreateKernel(programPointer, entryPoint), deviceContext);
            kernels.add(kernel);
        } catch (HIPException e) {
            throw new TornadoBailoutRuntimeException(e.getMessage());
        }

        return kernel;
    }

    public void dump() {
        final int numDevices = getNumDevices();
        new TornadoLogger().debug("Num devices: %d", numDevices);
    }

}
