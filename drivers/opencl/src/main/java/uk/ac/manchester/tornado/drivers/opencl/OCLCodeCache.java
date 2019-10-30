/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
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
import java.io.FileNotFoundException;
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
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLBuildStatus;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLCodeCache {

    public static final String LOOKUP_BUFFER_KERNEL_NAME = "lookupBufferAddress";
    private static final String DIRECTORY_BITSTREAM = "fpga-source-comp/";
    public static String FPGA_BIN_LOCATION = getProperty("tornado.fpga.bin", "./" + DIRECTORY_BITSTREAM + LOOKUP_BUFFER_KERNEL_NAME);

    private static final String FALSE = "False";
    private static final String BASH = "bash";
    private final String OPENCL_SOURCE_SUFFIX = ".cl";
    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", FALSE));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", FALSE));
    private final boolean OPENCL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.dump", FALSE));
    private final boolean OPENCL_PRINT_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.print", FALSE));
    private final boolean PRINT_LOAD_TIME = false;
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "/var/opencl-codecache");
    private final String OPENCL_SOURCE_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-compiler");
    private final String OPENCL_LOG_DIR = getProperty("tornado.opencl.source.dir", "/var/opencl-logs");
    private final String FPGA_SOURCE_DIR = getProperty("tornado.fpga.source.dir", DIRECTORY_BITSTREAM);
    private final HashSet<String> FPGA_FLAGS = new HashSet<>(Arrays.asList("-v", "-fast-compile", "-high-effort", "-fp-relaxed", "-report", "-incremental", "-profile"));
    private final String INTEL_ALTERA_OPENCL_COMPILER = "aoc";
    private final String INTEL_ALTERA_EMULATOR = "-march=emulator";
    private final String INTEL_NALLATECH_BOARD_NAME = "-board=p385a_sch_ax115";
    private final String INTEL_FPGA_COMPILATION_FLAGS = getProperty("tornado.fpga.flags", null);
    private final String FPGA_CLEANUP_SCRIPT = System.getenv("TORNADO_SDK") + "/bin/cleanFpga.sh";
    private final String FPGA_TASKSCHEDULE = "s0.t0.";
    private boolean COMPILED_BUFFER_KERNEL = false;

    /**
     * OpenCL Binary Options: -Dtornado.precompiled.binary=<path/to/binary,task>
     *
     * e.g.,
     *
     * <p>
     * <code>
     * -Dtornado.precompiled.binary=</tmp/saxpy,s0.t0.device=0:1>
     * </code>
     * </p>
     */
    private final StringBuffer OPENCL_BINARIES = TornadoOptions.FPGA_BINARIES;

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
            processPrecompiledBinaries();
        }

        // Composing the binary entry-point for the FPGA needs a
        // a Taskschedule and Task id as prefix which is currently
        // passed as constant FPGA_TASKSCHEDULE (e.g s0.t0.)
        if (Tornado.ACCELERATOR_IS_FPGA) {
            precompiledBinariesPerDevice = new HashMap<>();
            final String lookupBufferDeviceKernelName = FPGA_TASKSCHEDULE + String.format("device=%d:%d", deviceContext.getDevice().getIndex(), deviceContext.getPlatformContext().getPlatformIndex());
            precompiledBinariesPerDevice.put(lookupBufferDeviceKernelName, FPGA_BIN_LOCATION);
        }

        if (OPENCL_CACHE_ENABLE) {
            info("loading binaries into code cache");
            load();
        }
    }

    private void processPrecompiledBinaries() {
        String[] binaries = OPENCL_BINARIES.toString().split(",");

        if (binaries.length == 1) {
            // We try to parse a configuration file
            binaries = processPrecompiledBinariesFromFile(binaries[0]);
        } else if ((binaries.length % 2) != 0) {
            throw new RuntimeException("tornado.precompiled.binary=<path>,taskName.device");
        }

        for (int i = 0; i < binaries.length; i += 2) {
            String binaryFile = binaries[i];
            String taskAndDeviceInfo = binaries[i + 1];
            precompiledBinariesPerDevice.put(taskAndDeviceInfo, binaryFile);

            // For each entry, we should add also an entry for
            // lookup-buffer-address
            String device = taskAndDeviceInfo.split("\\.")[2];
            String kernelName = "oclbackend.lookupBufferAddress." + device;
            precompiledBinariesPerDevice.put(kernelName, binaryFile);
        }
    }

    private String[] processPrecompiledBinariesFromFile(String fileName) {
        StringBuilder listBinaries = new StringBuilder();
        BufferedReader fileContent = null;
        try {
            fileContent = new BufferedReader(new FileReader(fileName));
            String line = fileContent.readLine();
            while (line != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    listBinaries.append(line + ",");
                }
                line = fileContent.readLine();
            }
            listBinaries.deleteCharAt(listBinaries.length() - 1);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File: " + fileName + " not found");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fileContent.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return listBinaries.toString().split(",");
    }

    public boolean isLoadBinaryOptionEnabled() {
        return (OPENCL_BINARIES != null);
    }

    public String getOpenCLBinary(String taskName) {
        if (precompiledBinariesPerDevice != null) {
            return precompiledBinariesPerDevice.get(taskName);
        } else {
            return null;
        }
    }

    private Path resolveDirectory(String dir) {
        final String tornadoRoot = (Tornado.ACCELERATOR_IS_FPGA) ? System.getenv("PWD") : System.getenv("TORNADO_SDK");
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = (Tornado.ACCELERATOR_IS_FPGA) ? Paths.get(tornadoRoot + "/" + dir) : Paths.get(tornadoRoot + "/" + dir + "/" + deviceDir);
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

    private Path resolveBitstreamDirectory() {
        return resolveDirectory(FPGA_SOURCE_DIR);
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

    public boolean isKernelAvailable() {
        return kernelAvailable;
    }

    public void appendSourceToFile(String id, String entryPoint, byte[] source) {
        if (Tornado.ACCELERATOR_IS_FPGA) {
            final Path outDir = Tornado.ACCELERATOR_IS_FPGA ? resolveBitstreamDirectory() : resolveSourceDirectory();
            if (entryPoint.equals(LOOKUP_BUFFER_KERNEL_NAME)) {
                File file = new File(outDir + "/" + entryPoint + OPENCL_SOURCE_SUFFIX);
                RuntimeUtilities.writeStreamToFile(file, source, false);
            } else {
                File file = new File(outDir + "/" + LOOKUP_BUFFER_KERNEL_NAME + OPENCL_SOURCE_SUFFIX);
                RuntimeUtilities.writeStreamToFile(file, source, true);
            }
        }

    }

    private String[] composeIntelHLSCommand(String inputFile, String outputFile) {
        StringJoiner bufferCommand = new StringJoiner(" ");

        bufferCommand.add(INTEL_ALTERA_OPENCL_COMPILER);
        bufferCommand.add(inputFile);

        if (INTEL_FPGA_COMPILATION_FLAGS != null) {
            String[] flags = INTEL_FPGA_COMPILATION_FLAGS.split(",");
            for (String flag : flags) {
                if (FPGA_FLAGS.contains(flag)) {
                    bufferCommand.add(flag);
                }
            }
        }
        if (Tornado.FPGA_EMULATION) {
            bufferCommand.add(INTEL_ALTERA_EMULATOR);
        } else {
            bufferCommand.add(INTEL_NALLATECH_BOARD_NAME); // XXX: Specific to
                                                           // the FPGA model we
                                                           // currently have
        }
        bufferCommand.add("-o " + outputFile);
        return bufferCommand.toString().split(" ");
    }

    private String[] composeXilinxHLSCompileCommand(String inputFile, String kernelName) {
        StringJoiner bufferCommand = new StringJoiner(" ", "xocc ", "" );

        if(Tornado.FPGA_EMULATION) {
            bufferCommand.add("-t " + "sw_emu");
        } else {
            bufferCommand.add("-t " + "hw");
        }
        bufferCommand.add("--platform " + "xilinx_kcu1500_dynamic_5_0 " + "-c " +"-k " +kernelName);
        bufferCommand.add("-g " + "-I./" + DIRECTORY_BITSTREAM);
        bufferCommand.add("--xp " + "misc:solution_name=lookupBufferAddress");
        bufferCommand.add("--report_dir " + DIRECTORY_BITSTREAM + "reports");
        bufferCommand.add("--log_dir " + DIRECTORY_BITSTREAM + "logs");
        bufferCommand.add("-o " + DIRECTORY_BITSTREAM + kernelName + ".xo " + inputFile);

        return bufferCommand.toString().split(" ");
    }

    private String[] composeXilinxHLSLinkCommand(String kernelName) {
        StringJoiner bufferCommand = new StringJoiner(" ", "xocc ", "" );

        if(Tornado.FPGA_EMULATION) {
            bufferCommand.add("-t " + "sw_emu");
        } else {
            bufferCommand.add("-t " + "hw");
        }
        bufferCommand.add("--platform " + "xilinx_kcu1500_dynamic_5_0 " + "-l " +"-g");
        bufferCommand.add("--xp " + "misc:solution_name=link");
        bufferCommand.add("--report_dir " + DIRECTORY_BITSTREAM + "reports");
        bufferCommand.add("--log_dir " + DIRECTORY_BITSTREAM + "logs");
        bufferCommand.add("-O3 " + "-j12");
        bufferCommand.add("--remote_ip_cache " + DIRECTORY_BITSTREAM + "ip_cache");
        bufferCommand.add("-o " + DIRECTORY_BITSTREAM + LOOKUP_BUFFER_KERNEL_NAME + ".xclbin " + DIRECTORY_BITSTREAM + LOOKUP_BUFFER_KERNEL_NAME + ".xo " + DIRECTORY_BITSTREAM + kernelName + ".xo");

        return bufferCommand.toString().split(" ");
    }

    private void callOSforCompilation(String[] compilationCommand, String[] commandRename) {
        try {
            if(compilationCommand!=null)
                RuntimeUtilities.sysCall(compilationCommand, true);
            if(commandRename!=null)
                RuntimeUtilities.sysCall(commandRename, true);
        } catch (IOException e) {
            throw new TornadoRuntimeException(e);
        }
    }

    private boolean shouldGenerateXilinxBitstream(File fpgaBitStreamFile, OCLDeviceContext deviceContext) {
        if (!RuntimeUtilities.ifFileExists(fpgaBitStreamFile)) {
            if (deviceContext.getPlatformContext().getPlatform().getVendor().equals("Xilinx")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public OCLInstalledCode installFPGASource(String id, String entryPoint, byte[] source) { //TODO Override this method for each FPGA backend
        String[] compilationCommand;
        final String inputFile = FPGA_SOURCE_DIR + LOOKUP_BUFFER_KERNEL_NAME + OPENCL_SOURCE_SUFFIX;
        final String outputFile = FPGA_SOURCE_DIR + LOOKUP_BUFFER_KERNEL_NAME;
        File fpgaBitStreamFile = new File(FPGA_BIN_LOCATION);

        appendSourceToFile(id, entryPoint, source);
        if (!entryPoint.equals(LOOKUP_BUFFER_KERNEL_NAME)) {
            String[] commandRename;
            String[] linkCommand=null;

            if (OPENCL_PRINT_SOURCE) {
                String sourceCode = new String(source);
                System.out.println(sourceCode);
            }

            if(deviceContext.getPlatformContext().getPlatform().getVendor().equals("Xilinx")) {
                compilationCommand = composeXilinxHLSCompileCommand(inputFile, entryPoint);
                linkCommand = composeXilinxHLSLinkCommand(entryPoint);
            } else if (deviceContext.getPlatformContext().getPlatform().getVendor().equals("Intel(R) Corporation")) {
                 compilationCommand = composeIntelHLSCommand(inputFile, outputFile);
            } else {
                // Should not reach here
                throw new TornadoRuntimeException("FPGA vendor not supported.");
            }
            commandRename = new String[] { BASH, FPGA_CLEANUP_SCRIPT, deviceContext.getPlatformContext().getPlatform().getVendor() };

            Path path = Paths.get(FPGA_BIN_LOCATION);
            if (RuntimeUtilities.ifFileExists(fpgaBitStreamFile)) {
                return installEntryPointForBinaryForFPGAs(path, LOOKUP_BUFFER_KERNEL_NAME);
            } else {
                callOSforCompilation(compilationCommand, commandRename);
                if(deviceContext.getPlatformContext().getPlatform().getVendor().equals("Xilinx")) {
                    callOSforCompilation(linkCommand, null);
                }
            }
            return installEntryPointForBinaryForFPGAs(resolveBitstreamDirectory(), LOOKUP_BUFFER_KERNEL_NAME);
        } else {
            if(!COMPILED_BUFFER_KERNEL) {
                COMPILED_BUFFER_KERNEL = true;

                if (Tornado.ACCELERATOR_IS_FPGA) {
                    appendSourceToFile(id, entryPoint, source);
                }

                if (OPENCL_PRINT_SOURCE) {
                    String sourceCode = new String(source);
                    System.out.println(sourceCode);
                }

                if(shouldGenerateXilinxBitstream(fpgaBitStreamFile, deviceContext)) {
                    compilationCommand = composeXilinxHLSCompileCommand(inputFile, entryPoint);
                    callOSforCompilation(compilationCommand, null);
                }
            } else {
                return null;
            }
        }
        return null;
    }

    public OCLInstalledCode installSource(TaskMetaData meta, String id, String entryPoint, byte[] source) {

        info("Installing code for %s into code cache", entryPoint);
        final OCLProgram program = deviceContext.createProgramWithSource(source, new long[] { source.length });

        if (OPENCL_DUMP_SOURCE) {
            final Path outDir = resolveSourceDirectory();
            File file = new File(outDir + "/" + id + "-" + entryPoint + OPENCL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                error("unable to dump source: ", e.getMessage());
            }
        }

        if (Tornado.ACCELERATOR_IS_FPGA) {
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
            final Path outDir = resolveLogDirectory();
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
                final Path outDir = resolveCacheDirectory();
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
                final Path outDir = resolveCacheDirectory();
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
            final Path cacheDir = resolveCacheDirectory();
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
