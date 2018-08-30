/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_SUCCESS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.PRINT_COMPILE_TIMES;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;
import static uk.ac.manchester.tornado.runtime.common.Tornado.error;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;
import static uk.ac.manchester.tornado.runtime.common.Tornado.warn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLCodeCache {

    public static final String LOOKUP_BUFFER_KERNEL_NAME = "lookupBufferAddress";
    private final String OPENCL_SOURCE_SUFFIX = ".cl";
    private final String INTEL_FPGA_SUFFIX = ".aocx";
    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", "False"));
    private final boolean OPENCL_LOAD_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.loadbin", "False"));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", "False"));
    private final boolean OPENCL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.dump", "False"));
    private final boolean OPENCL_PRINT_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.print", "False"));
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "/var/opencl-codecache");
    private final String OPENCL_SOURCE_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-compiler");
    private final String OPENCL_LOG_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-logs");
    private final boolean PRINT_LOAD_TIME = false;
    private final String FPGA_SOURCE_DIR = getProperty("tornado.fpga.source.dir", "fpga-source-comp/");
    private final String FPGA_BIN_LOCATION = getProperty("tornado.fpga.bin", "./fpga-source-comp/lookupBufferAddress");
    private final HashSet<String> FPGA_FLAGS = new HashSet<>(Arrays.asList("v", "fast-compile", "high-effort", "fp-relaxed", "high-effort", "report", "incremental", "profile"));
    private final String INTEL_FPGA_COMPILATION_FLAGS = getProperty("tornado.fpga.flags", null);

    /**
     * OpenCL Binary Options: -Dtornado.precompiled.binary=<path/to/binary,task>
     *
     * e.g.
     *
     * <p>
     * <code>
     * -Dtornado.precompiled.binary=</tmp/saxpy,s0.t0.device=0:1>
     * </code>
     * </p>
     */
    private final String OPENCL_BINARIES = getProperty("tornado.precompiled.binary", null);

    /**
     * Configuration File with all paths to the OpenCl pre-compiled binaries:
     * -Dtornado.precompiled.listFile=<path/to/file>
     *
     * <p>
     * <code>
     * -Dtornado.precompiled.listFile=./fileConfigFPGAs
     * </code>
     * </p>
     *
     */
    private final String OPENCL_FILE_BINARIES = getProperty("tornado.precompiled.listFile", null);

    /**
     * List of all flags targeting AOC compiler -Dtornado.fpga.flags=flag1,....flagn
     *
     * <p>
     * <code>
     * -Dtornado.fpga.flags=flag1,....flagn
     * </code>
     * </p>
     *
     */

    private final boolean PRINT_WARNINGS = false;

    private final ConcurrentHashMap<String, OCLInstalledCode> cache;
    private final OCLDeviceContext deviceContext;

    private boolean kernelAvailable;

    private HashMap<String, String> precompiledBinariesPerDevice;

    public OCLCodeCache(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();

        if (OPENCL_BINARIES != null) {
            precompiledBinariesPerDevice = new HashMap<>();
            processPrecompiledBinaries(null);
        }

        if (OPENCL_FILE_BINARIES != null) {
            precompiledBinariesPerDevice = new HashMap<>();
            processPrecompiledBinariesFromFile();
        }
        if (OpenCL.ACCELERATOR_IS_FPGA) {
            precompiledBinariesPerDevice = new HashMap<>();
            // TODO : get this info from runtime
            String tempKernelName = "s0.t0.device=0:1";
            precompiledBinariesPerDevice.put(tempKernelName, FPGA_BIN_LOCATION);
        }

        if (OPENCL_CACHE_ENABLE) {
            info("loading binaries into code cache");
            load();
        }
    }

    private void processPrecompiledBinaries(String binList) {
        String[] binaries = null;

        if (binList == null) {
            binaries = OPENCL_BINARIES.split(",");
        } else {
            binaries = binList.split(",");
        }

        if ((binaries.length % 2) != 0) {
            throw new RuntimeException("tornado.precompiled.binary=<path> , device ");
        }

        for (int i = 0; i < binaries.length; i += 2) {
            String binaryFile = binaries[i];
            String taskAndDeviceInfo = binaries[i + 1];
            precompiledBinariesPerDevice.put(taskAndDeviceInfo, binaryFile);

            // For each entry, we should add also an entry for lookup-buffer
            String device = taskAndDeviceInfo.split("\\.")[2];
            String kernelName = "oclbackend.lookupBufferAddress." + device;
            precompiledBinariesPerDevice.put(kernelName, binaryFile);
        }
    }

    private boolean flagCorrectness(String[] flags) {
        // return Arrays.asList(FPGA_FLAGS).containsAll(Arrays.asList(flags));
        return FPGA_FLAGS.containsAll(Arrays.asList(flags));
    }

    private String[] processFPGAFlags() {
        String[] flags;
        flags = INTEL_FPGA_COMPILATION_FLAGS.split(",");
        if (INTEL_FPGA_COMPILATION_FLAGS != null && flagCorrectness(flags)) {
            for (int i = 0; i < flags.length; i++) {
                flags[i] = "-" + flags[i] + " ";
            }
        } else {
            flags[0] = " ";
        }
        return flags;
    }

    private void processPrecompiledBinariesFromFile() {
        StringBuilder listBinaries = new StringBuilder();
        BufferedReader fileContent = null;
        try {
            fileContent = new BufferedReader(new FileReader(OPENCL_FILE_BINARIES));
            String line = fileContent.readLine();

            while (line != null) {

                if (!line.isEmpty() && !line.startsWith("#")) {
                    listBinaries.append(line + ",");
                }
                line = fileContent.readLine();
            }
            listBinaries.deleteCharAt(listBinaries.length() - 1);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fileContent.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        processPrecompiledBinaries(listBinaries.toString());
    }

    public boolean isLoadBinaryOptionEnabled() {
        return OPENCL_LOAD_BINS;
    }

    public String getOpenCLBinary(String taskName) {
        if (precompiledBinariesPerDevice != null) {
            return precompiledBinariesPerDevice.get(taskName);
        } else {

            return null;
        }
    }

    private Path resolveDir(String dir) {
        final String tornadoRoot = (OpenCL.ACCELERATOR_IS_FPGA) ? System.getenv("TORNADO_ROOT") : System.getenv("TORNADO_SDK");
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = (OpenCL.ACCELERATOR_IS_FPGA) ? Paths.get(tornadoRoot + "/" + dir) : Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
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

    private Path resolveFPGADir() {
        return resolveDir(FPGA_SOURCE_DIR);
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

    public boolean isKernelAvailable() {
        return kernelAvailable;
    }

    public void appendSourceToFile(String id, String entryPoint, byte[] source) {

        if (OpenCL.ACCELERATOR_IS_FPGA) {
            final Path outDir = OpenCL.ACCELERATOR_IS_FPGA ? resolveFPGADir() : resolveSourceDir();
            if (entryPoint.equals("lookupBufferAddress")) {
                File file = new File(outDir + "/" + entryPoint + OPENCL_SOURCE_SUFFIX);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(source);
                    fos.close();
                } catch (IOException e) {
                    error("unable to dump source: ", e.getMessage());
                }
            } else {
                File file = new File(outDir + "/" + "lookupBufferAddress" + OPENCL_SOURCE_SUFFIX);
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write(source);
                    fos.close();
                } catch (IOException e) {
                    error("unable to dump source: ", e.getMessage());
                }
            }

        }

    }

    public OCLInstalledCode installSource(String id, String entryPoint, byte[] source, boolean isFPGA) {

        appendSourceToFile(id, entryPoint, source);

        if (!entryPoint.equals(LOOKUP_BUFFER_KERNEL_NAME)) {

            if (OPENCL_PRINT_SOURCE) {
                String sourceCode = new String(source);
                System.out.println(sourceCode);
            }

            String[] cmd,cmdRename;
            File f;

            String inputFile = FPGA_SOURCE_DIR + LOOKUP_BUFFER_KERNEL_NAME + OPENCL_SOURCE_SUFFIX;
            String outputFile = FPGA_SOURCE_DIR + LOOKUP_BUFFER_KERNEL_NAME;

            String hlsFlags = INTEL_FPGA_COMPILATION_FLAGS.equals(null) ? "-v" : String.join(" ", processFPGAFlags());

            if (OpenCL.FPGA_EMULATION) {
                cmd = new String[] { "aoc", inputFile, "-march=emulator", "-o", outputFile };
            } else {
                cmd = new String[] { "aoc", inputFile, "-board=p385a_sch_ax115", "-o", outputFile };
            }

            cmdRename = new String[] { "bash", "./bin/cleanFpga.sh" };

            f = new File(FPGA_BIN_LOCATION);

            cmd = new String[] { "aoc", inputFile, "-v", "-march=p385a_sch_ax115 ", "-report" };

            Path path = Paths.get(FPGA_BIN_LOCATION);
            if (RuntimeUtilities.ifFileExists(f)) {
                return installEntryPointForBinaryForFPGAs(path, LOOKUP_BUFFER_KERNEL_NAME);
            } else {
                RuntimeUtilities.sysCall(cmd, true);
                RuntimeUtilities.sysCall(cmdRename, true);

            }
            return installEntryPointForBinaryForFPGAs(resolveFPGADir(), LOOKUP_BUFFER_KERNEL_NAME);
        } else {
            // Should not reach here
            return null;
        }

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

        if (OpenCL.ACCELERATOR_IS_FPGA) {
            appendSourceToFile(id, entryPoint, source);
        }

        if (OPENCL_PRINT_SOURCE) {
            String sourceCode = new String(source);
            System.out.println(sourceCode);
        }

        // TODO add support for passing compiler optimisation flags here
        final long t0 = System.nanoTime();
        program.build(meta.getCompilerFlags());
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();

        if (PRINT_WARNINGS || (status == OCLBuildStatus.CL_BUILD_ERROR)) {
            if (!log.isEmpty()) {
                debug(log);
            }
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

        if (kernel != null) {
            kernelAvailable = true;
        }

        final OCLInstalledCode code = new OCLInstalledCode(entryPoint, source, deviceContext, program, kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (meta.shouldPrintCompileTimes()) {
                debug("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
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

        try {
            entryPoint = entryPoint.split("-")[1];
        } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {

        }
        long beforeLoad = (Tornado.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();
        OCLProgram program = deviceContext.createProgramWithBinary(binary, new long[] { binary.length });
        long afterLoad = (Tornado.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();

        if (PRINT_LOAD_TIME) {
            System.out.println("Binary load time: " + (afterLoad - beforeLoad) + (Tornado.TIME_IN_NANOSECONDS ? " ns" : " ms") + " \n");
        }

        if (program == null) {
            throw new OCLException("unable to load binary for " + entryPoint);
        }

        long t0 = System.nanoTime();
        program.build("");
        long t1 = System.nanoTime();

        OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
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
                RuntimeUtilities.writeToFile(outDir.toAbsolutePath().toString() + "/" + entryPoint, binary);
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

    public void reset() {
        for (OCLInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public OCLInstalledCode installEntryPointForBinaryForFPGAs(Path lookupPath, String entrypoint) {
        final File file = lookupPath.toFile();
        OCLInstalledCode lookupCode = null;
        if (file.length() == 0) {
            error("Empty input binary: %s (%s)", file);
        }
        try {
            final byte[] binary = Files.readAllBytes(lookupPath);
            lookupCode = installBinary(entrypoint, binary);
        } catch (OCLException | IOException e) {
            System.out.println("catch  :" + "\n");
            error("unable to load binary: %s (%s)", file, e.getMessage());
        }
        return lookupCode;
    }

    public boolean isCached(String id, String entryPoint) {
        return cache.containsKey(id + "-" + entryPoint);
    }

    public OCLInstalledCode getCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }
}
