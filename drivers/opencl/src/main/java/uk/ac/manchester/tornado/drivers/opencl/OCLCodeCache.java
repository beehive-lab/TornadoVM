/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.common.Tornado.PRINT_COMPILE_TIMES;
import static uk.ac.manchester.tornado.common.Tornado.debug;
import static uk.ac.manchester.tornado.common.Tornado.error;
import static uk.ac.manchester.tornado.common.Tornado.getProperty;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.common.Tornado.warn;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_SUCCESS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;

public class OCLCodeCache {

    private final String OPENCL_SOURCE_SUFFIX = ".cl";
    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", "False"));
    private final boolean OPENCL_LOAD_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.load", "False"));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", "False"));
    private final boolean OPENCL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.dump", "False"));
    private final boolean OPENCL_PRINT_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.print", "False"));
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "/var/opencl-codecache");
    private final String OPENCL_SOURCE_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-compiler");
    private final String OPENCL_LOG_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-logs");

    private final boolean PRINT_WARNINGS = false;

    private final Map<String, OCLInstalledCode> cache;
    private final OCLDeviceContext deviceContext;

    public OCLCodeCache(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new HashMap<>();

        if (OPENCL_CACHE_ENABLE || OPENCL_LOAD_BINS) {
            info("loading binaries into code cache");
            load();
        }
    }

    private Path resolveDir(String dir) {
        final String tornadoRoot = System.getenv("TORNADO_SDK");
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
        if (!Files.exists(outDir)) {
            try {
                Files.createDirectories(outDir);
            } catch (IOException e) {
                error("unable to create dir: %s", outDir.toString());
                error(e.getMessage());
            }
        }

        guarantee(Files.isDirectory(outDir), "target directory is not a directory: %s", outDir.toAbsolutePath().toString());
        return outDir;
    }

    private Path resolveCacheDir() {
        return resolveDir(OPENCL_CACHE_DIR);
    }

    private Path resolveSourceDir() {
        return resolveDir(OPENCL_SOURCE_DIR);
    }

    private Path resolveLogDir() {
        return resolveDir(OPENCL_LOG_DIR);
    }

    public OCLInstalledCode installSource(TaskMetaData meta, String id, String entryPoint, byte[] source) {

        info("Installing code for %s into code cache", entryPoint);
        final OCLProgram program = deviceContext.createProgramWithSource(source, new long[] { source.length });

        if (OPENCL_DUMP_SOURCE) {
            final Path outDir = resolveSourceDir();
            File file = new File(outDir + "/" + id + "-" + entryPoint + OPENCL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                error("unable to dump source: ", e.getMessage());
            }
        }

        if (OPENCL_PRINT_SOURCE) {
            String sourceCode = new String(source);
            System.out.println(sourceCode);
        }

        // TODO add support for passing compiler optimisation flags here
        final long t0 = System.nanoTime();
        program.build(meta.getOpenclCompilerFlags());
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            debug(log);
        }
        if (PRINT_WARNINGS || (status == OCLBuildStatus.CL_BUILD_ERROR)) {
            final Path outDir = resolveLogDir();
            final String identifier = id + "-" + entryPoint;
            error("Unable to compile task %s: check logs at %s/%s.log", identifier, outDir.toAbsolutePath(), identifier);

            File file = new File(outDir + "/" + identifier + ".log");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(log.getBytes());
            } catch (IOException e) {
                error("unable to write error log: ", e.getMessage());
            }
            file = new File(outDir + "/" + identifier + OPENCL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                error("unable to write error log: ", e.getMessage());
            }
        }

        final OCLKernel kernel = (status == CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;

        final OCLInstalledCode code = new OCLInstalledCode(entryPoint, source, deviceContext, program, kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (meta.shouldPrintCompileTimes()) {
                System.out.printf("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
            }
            cache.put(id + "-" + entryPoint, code);

            // BUG Apple does not seem to like implementing the OpenCL spec
            // properly, this causes a sigfault.
            if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !deviceContext.getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
                final Path outDir = resolveCacheDir();
                program.dumpBinaries(outDir.toAbsolutePath().toString() + "/" + entryPoint);
            }
        } else {
            warn("\tunable to compile %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    public OCLInstalledCode installBinary(String entryPoint, byte[] binary) throws OCLException {
        return installBinary(entryPoint, binary, false);
    }

    private OCLInstalledCode installBinary(String entryPoint, byte[] binary, boolean alreadyCached) throws OCLException {

        info("Installing binary for %s into code cache", entryPoint);
        final OCLProgram program = deviceContext.createProgramWithBinary(binary, new long[] { binary.length });

        if (program == null) {
            throw new OCLException("unable to load binary for " + entryPoint);
        }

        final long t0 = System.nanoTime();
        program.build("");
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            debug(log);
        }

        final OCLKernel kernel = (status == CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;

        final OCLInstalledCode code = new OCLInstalledCode(entryPoint, null, deviceContext, program, kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (PRINT_COMPILE_TIMES) {
                System.out.printf("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
            }
            cache.put(entryPoint, code);

            if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !alreadyCached) {
                final Path outDir = resolveCacheDir();
                writeToFile(outDir.toAbsolutePath().toString() + "/" + entryPoint, binary);
            }

        } else {
            warn("\tunable to install binary for %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    private void load() {
        try {
            final Path cacheDir = resolveCacheDir();
            Files.list(cacheDir).filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(this::loadBinary);
        } catch (IOException e) {
            error("io exception when loading cache files: %s", e.getMessage());
        }
    }

    private void loadBinary(Path path) {
        final File file = path.toFile();
        if (file.length() == 0) {
            return;
        }
        info("loading %s into cache", file.getAbsoluteFile());
        try {
            final byte[] binary = Files.readAllBytes(path);
            installBinary(file.getName(), binary, true);
        } catch (OCLException | IOException e) {
            error("unable to load binary: %s (%s)", file, e.getMessage());
        }
    }

    private void writeToFile(String file, byte[] binary) {

        info("dumping binary %s", file);
        try (FileOutputStream fis = new FileOutputStream(file);) {
            fis.write(binary);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        for (OCLInstalledCode code : cache.values()) {
            code.invalidate();
        }

        cache.clear();
    }

    public boolean isCached(String id, String entryPoint) {
        return cache.containsKey(id + "-" + entryPoint);
    }

    public OCLInstalledCode getCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }
}
