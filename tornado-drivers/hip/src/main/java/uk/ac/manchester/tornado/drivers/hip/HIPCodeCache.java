/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.hip.enums.HIPBuildStatus.CL_BUILD_SUCCESS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPBuildStatus;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceType;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class HIPCodeCache {

    private static final String FALSE = "False";
    private static final String TRUE = "True";
    private static final String CODE_OBJECT_SUFFIX = ".hsaco";
    private static final int SPIRV_MAGIC_NUMBER = 119734787;
    private static final String OPENCL_SOURCE_SUFFIX = ".cl";
    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", FALSE));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", FALSE));
    private final boolean OPENCL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.dump", FALSE));
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "/var/opencl-codecache");
    private final String OPENCL_SOURCE_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-compiler");
    private final String OPENCL_LOG_DIR = getProperty("tornado.opencl.log.dir", "/var/opencl-logs");

    /**
     * Persist compiled module images and reload them on the next run, so that a kernel is only handed to
     * hipRTC once per (source, device, flags, toolkit). Without it every process pays full hipRTC
     * compilation again, which dominates start-up for applications with many kernels.
     */
    private final boolean HIP_CODE_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.hip.codecache.enable", TRUE));
    private final String HIP_CODE_CACHE_DIR = getProperty("tornado.hip.codecache.dir", "/var/hip-codecache");

    private final ConcurrentHashMap<String, HIPInstalledCode> cache;
    private final HIPDeviceContextInterface deviceContext;
    private boolean kernelAvailable;

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    public HIPCodeCache(HIPDeviceContextInterface deviceContext) {
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
        final String tornadoRoot = Tornado.getTornadoHome();
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
        createOrReuseDirectory(outDir);
        return outDir;
    }

    private Path resolveCacheDirectory() {
        return resolveDirectory(OPENCL_CACHE_DIR);
    }

    private Path resolveModuleCacheDirectory() {
        return resolveDirectory(HIP_CODE_CACHE_DIR);
    }

    /**
     * Identity of everything that can change the compiled image: the kernel source, the device, its
     * driver, and the compiler flags. A code object is architecture-specific, so a cache hit on a
     * different GPU or after a ROCm upgrade must be impossible.
     */
    private String moduleCacheKey(byte[] source, String compilerFlags) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source);
            final HIPTargetDevice device = deviceContext.getDevice();
            final StringBuilder identity = new StringBuilder();
            identity.append(device.getDeviceName()).append('|') //
                    .append(device.getVersion()).append('|') //
                    .append(device.getDriverVersion()).append('|') //
                    .append(device.getArchitecture()).append('|') //
                    .append(HIPProgram.getHiprtcVersion()).append('|') //
                    .append(compilerFlags == null ? "" : compilerFlags);
            digest.update(identity.toString().getBytes(StandardCharsets.UTF_8));
            final byte[] hash = digest.digest();
            final StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private HIPProgram loadCachedModule(Path cacheFile, String entryPoint) {
        try {
            final byte[] image = Files.readAllBytes(cacheFile);
            if (image.length == 0) {
                return null;
            }
            logger.info("loading cached module for %s from %s", entryPoint, cacheFile);
            return deviceContext.createProgramWithBinary(image, new long[] { image.length });
        } catch (IOException | RuntimeException e) {
            // A stale or unreadable entry must never be fatal: fall back to compiling from source.
            logger.warn("unable to load cached module for %s (%s), recompiling", entryPoint, e.getMessage());
            return null;
        }
    }

    private void storeCachedModule(Path cacheFile, HIPProgram program, String entryPoint) {
        final byte[] image = program.getModuleImage();
        if (image == null || image.length == 0) {
            return;
        }
        try {
            // Write to a unique temporary file and move it into place, so that concurrent processes
            // never observe a partially written module.
            final Path temporary = Files.createTempFile(cacheFile.getParent(), cacheFile.getFileName().toString(), ".tmp");
            Files.write(temporary, image);
            try {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("cached module for %s in %s", entryPoint, cacheFile);
        } catch (IOException e) {
            logger.warn("unable to cache module for %s: %s", entryPoint, e.getMessage());
        }
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

    private void installCodeInCodeCache(HIPProgram program, String id, String entryPoint, HIPInstalledCode code) {
        cache.put(id + "-" + entryPoint, code);
        // BUG Apple does not seem to like implementing the HIPDriver spec
        // properly, this causes a SIGFAULT.
        if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !deviceContext.getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
            final Path outDir = resolveCacheDirectory();
            program.dumpBinaries(outDir.toAbsolutePath() + "/" + entryPoint);
        }
    }

    public HIPInstalledCode installSource(TaskDataContext meta, String id, String entryPoint, byte[] source) {

        logger.info("Installing code for %s into code cache", entryPoint);

        boolean isSPIRVBinary = isInputSourceSPIRVBinary(source);
        final String compilerFlags = meta.getCompilerFlags(TornadoVMBackendType.HIP);

        // Try the on-disk module cache first: a hit skips hipRTC entirely.
        Path moduleCacheFile = null;
        HIPProgram program = null;
        if (HIP_CODE_CACHE_ENABLE && !isSPIRVBinary) {
            final String key = moduleCacheKey(source, compilerFlags);
            if (key != null) {
                moduleCacheFile = resolveModuleCacheDirectory().resolve(entryPoint + "-" + key + CODE_OBJECT_SUFFIX);
                if (Files.exists(moduleCacheFile)) {
                    program = loadCachedModule(moduleCacheFile, entryPoint);
                }
            }
        }

        // Before compiling, not after: a failure is much easier to read next to the source that produced it.
        if (meta.isPrintKernelEnabled()) {
            RuntimeUtilities.dumpKernel(source);
        }

        final boolean loadedFromCache = program != null;
        if (program == null) {
            if (isSPIRVBinary) {
                program = deviceContext.createProgramWithIL(source, new long[] { source.length });
            } else {
                program = deviceContext.createProgramWithSource(source, new long[] { source.length });
            }
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

        if (deviceContext.getDevice().getDeviceType() == HIPDeviceType.CL_DEVICE_TYPE_ACCELERATOR) {
            appendSourceToFile(source, entryPoint);
        }

        logger.debug("\tOpenCL compiler flags = %s", compilerFlags);
        program.build(compilerFlags);
        final HIPBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        logger.debug("\tOpenCL compilation status = %s", status.toString());

        if (status == HIPBuildStatus.CL_BUILD_ERROR) {
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            System.err.println("\n[ERROR] TornadoVM JIT Compiler - HIPDriver Build Error Log:\n\n" + log + "\n");
            dumpKernelSource(id, entryPoint, log, source);
            throw new TornadoBailoutRuntimeException("Error during code compilation with the HIPDriver driver:\n" + log);
        }

        HIPKernel kernel = null;
        if (status == CL_BUILD_SUCCESS) {
            kernel = program.clCreateKernel(entryPoint);
            kernelAvailable = true;
        }

        if (status == CL_BUILD_SUCCESS && !loadedFromCache && moduleCacheFile != null) {
            storeCachedModule(moduleCacheFile, program, entryPoint);
        }

        final HIPInstalledCode code = new HIPInstalledCode(entryPoint, source, (HIPDeviceContext) deviceContext, program, kernel, isSPIRVBinary);
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
        for (HIPInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public boolean isCached(String key) {
        return cache.containsKey(key);
    }

    public HIPInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }
}
