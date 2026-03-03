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
package uk.ac.manchester.tornado.drivers.metal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalBuildStatus.METAL_BUILD_SUCCESS;
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
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalBuildStatus;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalCodeCache {

    private static final String FALSE = "False";
    private static final String METAL_SOURCE_SUFFIX = ".metal";
    private final boolean METAL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.metal.codecache.enable", FALSE));
    private final boolean METAL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.metal.codecache.dump", FALSE));
    private final boolean METAL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.metal.source.dump", FALSE));
    private final boolean PRINT_LOAD_TIME = false;
    private final String METAL_CACHE_DIR = getProperty("tornado.metal.codecache.dir", "/var/metal-codecache");
    private final String METAL_SOURCE_DIR = getProperty("tornado.metal.source.dir", "/var/metal-compiler");
    private final String METAL_LOG_DIR = getProperty("tornado.metal.log.dir", "/var/metal-logs");

    private final ConcurrentHashMap<String, MetalInstalledCode> cache;
    private final MetalDeviceContextInterface deviceContext;
    private boolean kernelAvailable;

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    public MetalCodeCache(MetalDeviceContextInterface deviceContext) {
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
        final String tornadoRoot = System.getenv("TORNADO_SDK");
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
        createOrReuseDirectory(outDir);
        return outDir;
    }

    private Path resolveCacheDirectory() {
        return resolveDirectory(METAL_CACHE_DIR);
    }

    private Path resolveSourceDirectory() {
        return resolveDirectory(METAL_SOURCE_DIR);
    }

    private Path resolveLogDirectory() {
        return resolveDirectory(METAL_LOG_DIR);
    }

    boolean isKernelAvailable() {
        return kernelAvailable;
    }

    /**
     * Compile the provided Metal Shading Language (MSL) source into a .metallib
     * artifact using the Xcode command line tools (xcrun). The method writes the
     * source to the source directory, invokes `xcrun metal` and `xcrun metallib`,
     * and installs the resulting binary into the code cache by delegating to
     * {@link #installBinary(String, String, byte[])}.
     *
     * This implementation is conservative: it only attempts to run the toolchain
     * when the commands are present on PATH and will surface clear errors
     * otherwise. When the cache is enabled and a metallib already exists on
     * disk for the normalized kernel name, the cached artifact will be used.
     *
     * @param kernelName kernel entry-point name (will be normalised)
     * @param mslSource  emitted MSL source
     * @return installed code record for the generated metallib
     */
    public MetalInstalledCode compileAndLink(String kernelName, String mslSource) {
        final String normalized = kernelName.replace('$', '_');
        final Path outDir = resolveSourceDirectory();
        final String sourceFileName = normalized + METAL_SOURCE_SUFFIX;
        final String airFileName = normalized + ".air";
        final String metallibFileName = normalized + ".metallib";

        final Path metalSource = outDir.resolve(sourceFileName);
        final Path airPath = outDir.resolve(airFileName);
        final Path metallibPath = outDir.resolve(metallibFileName);

        // Reuse existing metallib when present and cache enabled
        if (METAL_CACHE_ENABLE && Files.exists(metallibPath)) {
            try {
                final byte[] bin = Files.readAllBytes(metallibPath);
                return installBinary(normalized, normalized, bin);
            } catch (IOException | MetalException e) {
                logger.error("[Metal] failed to reuse cached metallib %s: %s", metallibPath, e.getMessage());
                // fall through to attempt rebuild
            }
        }

        // Write source
        try {
            Files.createDirectories(outDir);
            Files.write(metalSource, mslSource.getBytes());
        } catch (IOException e) {
            throw new TornadoRuntimeException("unable to write Metal source to " + metalSource + " : " + e.getMessage());
        }

        // Compose commands
        final String[] compileCmd = new String[] { "xcrun", "-sdk", "macosx", "metal", "-c", metalSource.toString(), "-o", airPath.toString() };
        final String[] linkCmd = new String[] { "xcrun", "-sdk", "macosx", "metallib", airPath.toString(), "-o", metallibPath.toString() };

        // Helper to run a command and capture output
        final StringBuilder toolOutput = new StringBuilder();
        java.util.function.BiFunction<String[], Path, Integer> runCmd = (cmd, workdir) -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (workdir != null) {
                    pb.directory(workdir.toFile());
                }
                pb.redirectErrorStream(true);
                final Process p = pb.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("[Metal-tool] %s", line);
                        toolOutput.append(line).append('\n');
                    }
                }
                int rc = p.waitFor();
                return rc;
            } catch (IOException | InterruptedException e) {
                logger.error("[Metal] tool invocation failed: %s", e.getMessage());
                toolOutput.append("[Metal] tool invocation failed: ").append(e.getMessage()).append('\n');
                return -1;
            }
        };

        int rc = runCmd.apply(compileCmd, outDir);
        if (rc != 0) {
            try {
                dumpKernelSource(normalized, normalized, toolOutput.toString(), mslSource.getBytes());
            } catch (Exception e) {
                logger.error("failed to write metal compiler log: %s", e.getMessage());
            }
            throw new TornadoCompilationException("Metal compiler failed for " + metalSource + " (rc=" + rc + ")\n" + toolOutput.toString());
        }

        // Clear captured output before linking
        toolOutput.setLength(0);

        rc = runCmd.apply(linkCmd, outDir);
        if (rc != 0) {
            try {
                dumpKernelSource(normalized, normalized, toolOutput.toString(), mslSource.getBytes());
            } catch (Exception e) {
                logger.error("failed to write metallib link log: %s", e.getMessage());
            }
            throw new TornadoCompilationException("metallib creation failed for " + airPath + " (rc=" + rc + ")\n" + toolOutput.toString());
        }

        // Read metallib and install
        try {
            final byte[] bin = Files.readAllBytes(metallibPath);
            if (METAL_DUMP_BINS) {
                final Path cacheOut = resolveCacheDirectory();
                try {
                    Files.createDirectories(cacheOut);
                    Files.copy(metallibPath, cacheOut.resolve(metallibFileName));
                } catch (IOException e) {
                    logger.error("[Metal] unable to dump metallib to cache: %s", e.getMessage());
                }
            }
            return installBinary(normalized, normalized, bin);
        } catch (IOException | MetalException e) {
            throw new TornadoRuntimeException("unable to read/install generated metallib: " + e.getMessage());
        }
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
        file = new File(outDir + "/" + identifier + METAL_SOURCE_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(source);
        } catch (IOException e) {
            logger.error("unable to write error log: ", e.getMessage());
        }
    }

    private void installCodeInCodeCache(MetalProgram program, String id, String entryPoint, MetalInstalledCode code) {
        cache.put(id + "-" + entryPoint, code);
        if (METAL_CACHE_ENABLE || METAL_DUMP_BINS) {
            final Path outDir = resolveCacheDirectory();
            program.dumpBinaries(outDir.toAbsolutePath() + "/" + entryPoint);
        }
    }

    public MetalInstalledCode installSource(TaskDataContext meta, String id, String entryPoint, byte[] source) {
        logger.info("Installing code for %s into code cache", entryPoint);

        final MetalProgram program = deviceContext.createProgramWithSource(source, new long[] { source.length });

        if (METAL_DUMP_SOURCE) {
            final Path outDir = resolveSourceDirectory();
            File file = new File(outDir + "/" + id + "-" + entryPoint + METAL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                logger.error("unable to dump source: ", e.getMessage());
            }
        }

        if (meta.isPrintKernelEnabled()) {
            RuntimeUtilities.dumpKernel(source);
        }
        logger.debug("\tMetal compiler flags = %s", meta.getCompilerFlags(TornadoVMBackendType.METAL));
        program.build(meta.getCompilerFlags(TornadoVMBackendType.METAL));
        final MetalBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        logger.debug("\tMetal compilation status = %s", status.toString());

        if (status == MetalBuildStatus.METAL_BUILD_ERROR) {
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            System.err.println("\n[ERROR] TornadoVM JIT Compiler - Metal Build Error Log:\n\n" + log + "\n");
            dumpKernelSource(id, entryPoint, log, source);
            throw new TornadoBailoutRuntimeException("Error during code compilation with the Metal driver");
        }

        MetalKernel kernel = null;
        if (status == METAL_BUILD_SUCCESS) {
            kernel = program.metalCreateKernel(entryPoint);
            kernelAvailable = true;
        } else {
            logger.debug("Kernel not created - status is %s", status);
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            logger.debug("Build log: %s", log);
        }

        final MetalInstalledCode code = new MetalInstalledCode(entryPoint, source, (MetalDeviceContext) deviceContext, program, kernel, false);
        if (status == METAL_BUILD_SUCCESS) {
            logger.debug("\tMetal Kernel id = 0x%x", kernel.getMetalKernelID());
            installCodeInCodeCache(program, id, entryPoint, code);
        } else {
            logger.warn("\tunable to compile %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    private MetalInstalledCode installBinary(String id, String entryPoint, byte[] binary) throws MetalException {
        logger.info("Installing binary for %s into code cache", entryPoint);

        if (entryPoint.contains("-")) {
            entryPoint = entryPoint.split("-")[1];
        }

        long beforeLoad = (TornadoOptions.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();
        MetalProgram program = deviceContext.createProgramWithBinary(binary, new long[] { binary.length });
        long afterLoad = (TornadoOptions.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();

        if (PRINT_LOAD_TIME) {
            System.out.println("Binary load time: " + (afterLoad - beforeLoad) + (TornadoOptions.TIME_IN_NANOSECONDS ? " ns" : " ms") + "\n");
        }

        if (program == null) {
            throw new MetalException("unable to load binary for " + entryPoint);
        }

        program.build("");

        final MetalBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        logger.debug("\tMetal compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            logger.debug(log);
        }

        final MetalKernel kernel = (status == METAL_BUILD_SUCCESS) ? program.metalCreateKernel(entryPoint) : null;
        final MetalInstalledCode code = new MetalInstalledCode(entryPoint, binary, (MetalDeviceContext) deviceContext, program, kernel, false);

        if (status == METAL_BUILD_SUCCESS) {
            logger.debug("\tMetal Kernel id = 0x%x", kernel.getMetalKernelID());
            cache.put(entryPoint, code);

            if (METAL_CACHE_ENABLE || METAL_DUMP_BINS) {
                final Path outDir = resolveCacheDirectory();
                RuntimeUtilities.writeToFile(outDir.toAbsolutePath().toString() + "/" + entryPoint, binary);
            }
        } else {
            logger.warn("\tunable to install binary for %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    public void reset() {
        for (MetalInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public boolean isCached(String key) {
        return cache.containsKey(key);
    }

    public MetalInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }
}
