/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_SUCCESS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class OCLCodeCache {

    private static final String FALSE = "False";
    private static final int SPIRV_MAGIC_NUMBER = 119734787;
    private static final String OPENCL_SOURCE_SUFFIX = ".cl";
    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", FALSE));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", FALSE));
    private final boolean OPENCL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.dump", FALSE));
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "/var/opencl-codecache");
    private final String OPENCL_SOURCE_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-compiler");
    private final String OPENCL_LOG_DIR = getProperty("tornado.opencl.log.dir", "/var/opencl-logs");

    private final ConcurrentHashMap<String, OCLInstalledCode> cache;
    private final OCLDeviceContextInterface deviceContext;
    private boolean kernelAvailable;

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    public OCLCodeCache(OCLDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    private void createOrReuseDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                logger.error("unable to create dir: %s", dir.toString());
                logger.error(e.getMessage());
            }
        }
        guarantee(Files.isDirectory(dir), "target directory is not a directory: %s", dir.toAbsolutePath().toString());
    }

    private Path resolveDirectory(String dir) {
        final String tornadoRoot = System.getenv("TORNADOVM_HOME");
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
        createOrReuseDirectory(outDir);
        return outDir;
    }

    private Path resolveCacheDirectory() {
        return resolveDirectory(OPENCL_CACHE_DIR);
    }

    private Path resolveSourceDirectory() {
        return resolveDirectory(OPENCL_SOURCE_DIR);
    }

    private Path resolveLogDirectory() {
        return resolveDirectory(OPENCL_LOG_DIR);
    }

    boolean isKernelAvailable() {
        return kernelAvailable;
    }

    private void appendSourceToFile(byte[] source, String entryPoint) {
        final Path outDir = resolveSourceDirectory();
        File file = new File(outDir + "/" + entryPoint + OPENCL_SOURCE_SUFFIX);
        RuntimeUtilities.writeStreamToFile(file, source, false);
    }

    private boolean isInputSourceSPIRVBinary(byte[] source) {
        // Check the header of this binary for the SPIRV-header number
        int value = (source[0] & 255) + ((source[1] & 255) << 8) + ((source[2] & 255) << 16) + ((source[3] & 255) << 24);
        return value == SPIRV_MAGIC_NUMBER;
    }

    private void dumpKernelSource(String id, String entryPoint, String log, byte[] source) {
        final Path outDir = resolveLogDirectory();
        final String identifier = id + "-" + entryPoint;
        logger.error("Unable to compile task %s: check logs at %s/%s.log", identifier, outDir.toAbsolutePath(), identifier);

        File file = new File(outDir + "/" + identifier + ".log");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(log.getBytes());
        } catch (IOException e) {
            logger.error("unable to write error log: ", e.getMessage());
        }
        file = new File(outDir + "/" + identifier + OPENCL_SOURCE_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(source);
        } catch (IOException e) {
            logger.error("unable to write error log: ", e.getMessage());
        }

    }

    private void installCodeInCodeCache(OCLProgram program, String id, String entryPoint, OCLInstalledCode code) {
        cache.put(id + "-" + entryPoint, code);
        // BUG Apple does not seem to like implementing the OpenCL spec
        // properly, this causes a SIGFAULT.
        if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !deviceContext.getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
            final Path outDir = resolveCacheDirectory();
            program.dumpBinaries(outDir.toAbsolutePath() + "/" + entryPoint);
        }
    }

    public OCLInstalledCode installSource(TaskDataContext meta, String id, String entryPoint, byte[] source) {

        logger.info("Installing code for %s into code cache", entryPoint);

        boolean isSPIRVBinary = isInputSourceSPIRVBinary(source);
        final OCLProgram program;
        if (isSPIRVBinary) {
            program = deviceContext.createProgramWithIL(source, new long[] { source.length });
        } else {
            program = deviceContext.createProgramWithSource(source, new long[] { source.length });
        }

        if (OPENCL_DUMP_SOURCE) {
            final Path outDir = resolveSourceDirectory();
            File file = new File(outDir + "/" + id + "-" + entryPoint + OPENCL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                logger.error("unable to dump source: ", e.getMessage());
            }
        }

        if (deviceContext.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR) {
            appendSourceToFile(source, entryPoint);
        }

        if (meta.isPrintKernelEnabled()) {
            RuntimeUtilities.dumpKernel(source);
        }
        logger.debug("\tOpenCL compiler flags = %s", meta.getCompilerFlags(TornadoVMBackendType.OPENCL));
        program.build(meta.getCompilerFlags(TornadoVMBackendType.OPENCL));
        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        logger.debug("\tOpenCL compilation status = %s", status.toString());

        if (status == OCLBuildStatus.CL_BUILD_ERROR) {
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            System.err.println("\n[ERROR] TornadoVM JIT Compiler - OpenCL Build Error Log:\n\n" + log + "\n");
            dumpKernelSource(id, entryPoint, log, source);
            throw new TornadoBailoutRuntimeException("Error during code compilation with the OpenCL driver");
        }

        OCLKernel kernel = null;
        if (status == CL_BUILD_SUCCESS) {
            kernel = program.clCreateKernel(entryPoint);
            kernelAvailable = true;
        }

        final OCLInstalledCode code = new OCLInstalledCode(entryPoint, source, (OCLDeviceContext) deviceContext, program, kernel, isSPIRVBinary);
        if (status == CL_BUILD_SUCCESS) {
            logger.debug("\tOpenCL Kernel id = 0x%x", kernel.getOclKernelID());
            installCodeInCodeCache(program, id, entryPoint, code);
        } else {
            logger.warn("\tunable to compile %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    public void reset() {
        for (OCLInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public boolean isCached(String key) {
        return cache.containsKey(key);
    }

    public OCLInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }
}
