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
import static uk.ac.manchester.tornado.drivers.metal.enums.MetalBuildStatus.CL_BUILD_SUCCESS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalBuildStatus;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.drivers.metal.exceptions.MetalException;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalCodeCache {

    private static final String FALSE = "False";
    private static final int SPIRV_MAGIC_NUMBER = 119734787;
    private static final String METAL_SOURCE_SUFFIX = ".cl";
    private final boolean METAL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.metal.codecache.enable", FALSE));
    private final boolean METAL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.metal.codecache.dump", FALSE));
    private final boolean METAL_DUMP_SOURCE = Boolean.parseBoolean(getProperty("tornado.metal.source.dump", FALSE));
    private final boolean PRINT_LOAD_TIME = false;
    private final String METAL_CACHE_DIR = getProperty("tornado.metal.codecache.dir", "/var/metal-codecache");
    private final String METAL_SOURCE_DIR = getProperty("tornado.metal.source.dir", "/var/metal-compiler");
    private final String METAL_LOG_DIR = getProperty("tornado.metal.log.dir", "/var/metal-logs");
    private static final String DEFAULT_FPGA_CONFIGURATION_FILE_FOR_INTEL = "/etc/intel-fpga.conf";
    private static final String DEFAULT_FPGA_CONFIGURATION_FILE_FOR_INTEL_ONEAPI = "/etc/intel-oneapi-fpga.conf";
    private static final String DEFAULT_FPGA_CONFIGURATION_FILE_FOR_XILINX = "/etc/xilinx-fpga.conf";
    private final String FPGA_CONFIGURATION_FILE = getProperty("tornado.fpga.conf.file", null);
    private static final String FPGA_CLEANUP_SCRIPT = System.getenv("TORNADO_SDK") + "/bin/cleanFpga.sh";
    private static final String FPGA_AWS_AFI_SCRIPT = System.getenv("TORNADO_SDK") + "/bin/aws_post_processing.sh";

    /**
     * Metal Binary Options: -Dtornado.precompiled.binary=<path/to/binary,task>
     *
     * e.g.,
     *
     * <p>
     * <code>
     * -Dtornado.precompiled.binary=</tmp/saxpy,s0.t0.device=0:1>
     * </code>
     * </p>
     */
    private final StringBuilder METAL_BINARIES = TornadoOptions.FPGA_BINARIES;
    private final ConcurrentHashMap<String, MetalInstalledCode> cache;
    private final MetalDeviceContextInterface deviceContext;
    private String fpgaName;
    private String fpgaCompiler;
    private String compilationFlags;
    private String directoryBitstream;
    private boolean isFPGAInAWS;
    private String fpgaSourceDir;
    // ID -> KernelName (TaskName)
    private ConcurrentHashMap<String, ArrayList<Pair>> pendingTasks;
    private ArrayList<String> linkObjectFiles;
    private boolean kernelAvailable;

    private HashMap<String, String> precompiledBinariesPerDevice;

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    public MetalCodeCache(MetalDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
        pendingTasks = new ConcurrentHashMap<>();
        linkObjectFiles = new ArrayList<>();

        if (deviceContext.isPlatformFPGA()) {
            precompiledBinariesPerDevice = new HashMap<>();
            parseFPGAConfigurationFile();
            if (METAL_BINARIES != null) {
                processPrecompiledBinaries();
            }
        }
    }

    private String trimFirstSpaceFromString(String string) {
        return string.replaceFirst("\\s+", "");
    }

    private boolean tokenStartsAComment(String token) {
        return token.startsWith("#");
    }

    private boolean isQuartusHLSRequired() {
        return (fpgaCompiler.equals("aoc"));
    }

    private void assertIfQuartusHLSIsPresent() {
        if (System.getenv("QUARTUS_ROOT_DIR") == null) {
            throw new TornadoRuntimeException(
                    "[ERROR] The FPGA compiler (" + fpgaCompiler + ") requires the installation of the Intel(R) Quartus(R) Prime software. You can check if Quartus is installed and whether the QUARTUS_ROOT_DIR variable is properly set.");
        }
    }

    private boolean runOnIntelFPGAWithOneAPI() {
        return (System.getenv("ONEAPI_ROOT") != null);
    }

    private String fetchFPGAConfigurationFile() {
        if (deviceContext.getDevice().getDeviceVendor().equalsIgnoreCase("xilinx")) {
            return DEFAULT_FPGA_CONFIGURATION_FILE_FOR_XILINX;
        } else {
            if (runOnIntelFPGAWithOneAPI()) {
                return DEFAULT_FPGA_CONFIGURATION_FILE_FOR_INTEL_ONEAPI;
            } else {
                return DEFAULT_FPGA_CONFIGURATION_FILE_FOR_INTEL;
            }
        }
    }

    private String resolveFPGAConfigurationFileName() {
        if (FPGA_CONFIGURATION_FILE != null) {
            return FPGA_CONFIGURATION_FILE;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(System.getenv("TORNADO_SDK"));
            sb.append(fetchFPGAConfigurationFile());
            return sb.toString();
        }
    }

    private void parseFPGAConfigurationFile() {
        FileReader fileReader;
        BufferedReader bufferedReader;
        try {
            fileReader = new FileReader(resolveFPGAConfigurationFileName());
            bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = trimFirstSpaceFromString(line);
                StringTokenizer tokenizer = new StringTokenizer(line, " =");
                while (tokenizer.hasMoreElements()) {
                    String token = tokenizer.nextToken();
                    if (tokenStartsAComment(token)) {
                        break;
                    }

                    switch (token) {
                        case "DEVICE_NAME" -> fpgaName = tokenizer.nextToken(" =");
                        case "COMPILER" -> fpgaCompiler = tokenizer.nextToken(" =");
                        case "DIRECTORY_BITSTREAM" -> {
                            directoryBitstream = resolveAbsoluteDirectory(tokenizer.nextToken(" ="));
                            fpgaSourceDir = directoryBitstream;
                        }
                        case "FLAGS" -> {
                            StringBuilder buildFlags = new StringBuilder();

                            // Iterate over tokens that correspond to multiple flags
                            while (tokenizer.hasMoreElements()) {
                                String flag = tokenizer.nextToken(" =");
                                if (tokenStartsAComment(flag)) {
                                    break;
                                } else if (flag.contains("-")) {
                                    if (compilationFlags == null) {
                                        compilationFlags = resolveCompilationFlags(tokenizer, buildFlags, flag);
                                    } else {
                                        if (buildFlags.toString().isEmpty()) {
                                            buildFlags.append(compilationFlags);
                                        }
                                        compilationFlags = resolveCompilationFlags(tokenizer, buildFlags.append(" "), flag);
                                    }
                                }
                            }
                        }
                        case "AWS_ENV" -> isFPGAInAWS = tokenizer.nextToken(" =").toLowerCase().equals("yes");
                        default -> {
                        }
                    }
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Wrong configuration file or invalid settings. Please ensure that you have configured the configuration file with valid options!");
            System.exit(1);
        }
    }

    private String resolveCompilationFlags(StringTokenizer tokenizer, StringBuilder buildFlags, String flag) {
        String resolvedFlags;
        if (flag.contains("--config")) {
            String fileString = resolveAbsoluteDirectory(tokenizer.nextToken(" ="));
            resolvedFlags = buildFlags.append(flag).append(" ").append(fileString).toString();
        } else if (flag.contains("--")) {
            String fileString = tokenizer.nextToken(" =");
            resolvedFlags = buildFlags.append(flag).append(" ").append(fileString).toString();
        } else {
            resolvedFlags = buildFlags.append(flag).toString();
        }
        return resolvedFlags;
    }

    private void processPrecompiledBinaries() {
        String[] binaries = METAL_BINARIES.toString().split(",");

        if (binaries.length == 1) {
            // We try to parse a configuration file
            binaries = processPrecompiledBinariesFromFile(binaries[0]);
        } else if ((binaries.length % 2) != 0) {
            throw new RuntimeException("tornado.precompiled.binary=<path>,taskName.device");
        }

        for (int i = 0; i < binaries.length; i += 2) {
            String binaryFile = binaries[i];
            String taskAndDeviceInfo = binaries[i + 1];
            String task = taskAndDeviceInfo.split("\\.")[0] + "." + taskAndDeviceInfo.split("\\.")[1];
            String[] driverAndDevice = taskAndDeviceInfo.split("=")[1].split(":");
            int driverIndex = Integer.parseInt(driverAndDevice[0]);
            int deviceIndex = Integer.parseInt(driverAndDevice[1]);
            addNewEntryInBitstreamHashMap(task, binaryFile, driverIndex, deviceIndex);

            // For each entry, we should add also an entry for
            // lookup-buffer-address
            addNewEntryInBitstreamHashMap("oclbackend.lookupBufferAddress", binaryFile, driverIndex, deviceIndex);
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
            throw new TornadoCompilationException(e.getMessage());
        } finally {
            try {
                fileContent.close();
            } catch (IOException e) {
                throw new TornadoCompilationException(e.getMessage());
            }
        }
        return listBinaries.toString().split(",");
    }

    public boolean isLoadBinaryOptionEnabled() {
        return (METAL_BINARIES != null);
    }

    public String getMetalBinary(String taskName) {
        if (precompiledBinariesPerDevice != null) {
            return precompiledBinariesPerDevice.get(taskName);
        } else {
            return null;
        }
    }

    private String resolveAbsoluteDirectory(String dir) {
        final String tornadoRoot = (deviceContext.isPlatformFPGA()) ? System.getenv("PWD") : System.getenv("TORNADO_SDK");
        if (Paths.get(dir).isAbsolute()) {
            if (!Files.exists(Paths.get(dir))) {
                throw new TornadoRuntimeException("invalid directory: " + dir);
            }
            return dir;
        } else {
            return (tornadoRoot + "/" + dir);
        }
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

    private Path resolveBitstreamDirectory() {
        Path outDir = Paths.get(directoryBitstream);
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

    private void appendSourceToFile(byte[] source, String entryPoint) {
        final Path outDir = deviceContext.isPlatformFPGA() ? resolveBitstreamDirectory() : resolveSourceDirectory();
        File file = new File(outDir + "/" + entryPoint + METAL_SOURCE_SUFFIX);
        RuntimeUtilities.writeStreamToFile(file, source, false);
    }

    private String[] composeIntelHLSCommand(String inputFile, String outputFile) {
        StringJoiner bufferCommand = new StringJoiner(" ");

        bufferCommand.add(fpgaCompiler);
        bufferCommand.add(inputFile);

        if (compilationFlags != null) {
            bufferCommand.add(compilationFlags);
        }
        bufferCommand.add(TornadoOptions.FPGA_EMULATION ? ("-march=emulator") : ("-board=" + fpgaName));
        bufferCommand.add("-o " + outputFile);
        return bufferCommand.toString().split(" ");
    }

    private String[] composeIntelHLSCommandForOneAPI(String inputFile, String outputFile) {
        StringJoiner bufferCommand = new StringJoiner(" ");

        bufferCommand.add(fpgaCompiler);
        bufferCommand.add("--input=" + inputFile);
        bufferCommand.add("--device=" + fpgaName + " --cmd=build");
        bufferCommand.add("--ir=" + outputFile + ".aocx");

        return bufferCommand.toString().split(" ");
    }

    private String[] composeXilinxHLSCompileCommand(String inputFile, String kernelName) {
        StringJoiner bufferCommand = new StringJoiner(" ");

        bufferCommand.add(fpgaCompiler);

        bufferCommand.add(TornadoOptions.FPGA_EMULATION ? ("-t " + "sw_emu") : ("-t " + "hw"));
        bufferCommand.add("--platform " + fpgaName + " -c " + "-k " + kernelName);
        bufferCommand.add("-g " + "-I" + directoryBitstream);
        bufferCommand.add("--xp " + "misc:solution_name=" + kernelName);
        bufferCommand.add("--report_dir " + directoryBitstream + "reports");
        bufferCommand.add("--log_dir " + directoryBitstream + "logs");
        bufferCommand.add("-o " + directoryBitstream + kernelName + ".xo " + inputFile);

        return bufferCommand.toString().split(" ");
    }

    private void addObjectKernelsToLinker(StringJoiner bufferCommand) {
        for (String kernelNameObject : linkObjectFiles) {
            bufferCommand.add(directoryBitstream + kernelNameObject + ".xo");
        }
    }

    private String[] composeXilinxHLSLinkCommand(String entryPoint) {
        StringJoiner bufferCommand = new StringJoiner(" ");

        bufferCommand.add(fpgaCompiler);
        bufferCommand.add(TornadoOptions.FPGA_EMULATION ? ("-t " + "sw_emu") : ("-t " + "hw"));
        bufferCommand.add("--platform " + fpgaName + " -l " + "-g");
        bufferCommand.add("--xp " + "misc:solution_name=link");
        bufferCommand.add("--report_dir " + directoryBitstream + "reports");
        bufferCommand.add("--log_dir " + directoryBitstream + "logs");
        if (compilationFlags != null) {
            bufferCommand.add(compilationFlags);
        }
        bufferCommand.add("--remote_ip_cache " + directoryBitstream + "ip_cache");
        bufferCommand.add("-o " + directoryBitstream + entryPoint + ".xclbin");
        addObjectKernelsToLinker(bufferCommand);
        return bufferCommand.toString().split(" ");
    }

    private void invokeShellCommand(String[] command) {
        try {
            if (command != null) {
                RuntimeUtilities.systemCall(command, TornadoOptions.FULL_DEBUG, directoryBitstream);
            }
        } catch (IOException e) {
            throw new TornadoRuntimeException(e);
        }
    }

    private boolean shouldGenerateXilinxBitstream(File fpgaBitStreamFile, MetalDeviceContextInterface deviceContext) {
        if (!fpgaBitStreamFile.exists()) {
            return (deviceContext.getPlatformContext().getPlatform().getVendor().equals("Xilinx"));
        } else {
            return false;
        }
    }

    private boolean isPlatform(String platformName) {
        return deviceContext.getPlatformContext().getPlatform().getVendor().toLowerCase().startsWith(platformName);
    }

    private String[] splitTaskGraphAndTaskName(String id) {
        if (id.contains(".")) {
            String[] names = id.split("\\.");
            return names;
        }
        return new String[] { id };
    }

    private void addNewEntryInBitstreamHashMap(String id, String bitstreamDirectory) {
        String[] driverAndDevice = Tornado.getProperty(id + ".device", "0:0").split(":");
        addNewEntryInBitstreamHashMap(id, bitstreamDirectory, Integer.parseInt(driverAndDevice[0]), Integer.parseInt(driverAndDevice[1]));
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
        final String sourceFileName = normalized + ".metal";
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
            // Dump compiler output and source to log directory to aid debugging
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

    private void addNewEntryInBitstreamHashMap(String id, String bitstreamDirectory, int driverIndex, int deviceIndex) {
        if (precompiledBinariesPerDevice != null) {
            String lookupBufferDeviceKernelName = id + String.format(".device=%s:%s", driverIndex, deviceIndex);
            precompiledBinariesPerDevice.put(lookupBufferDeviceKernelName, bitstreamDirectory);
        }
    }

    private String getDeviceVendor() {
        return deviceContext.getPlatformContext().getPlatform().getVendor().toLowerCase().split("\\(")[0];
    }

    MetalInstalledCode installFPGASource(String id, String entryPoint, byte[] source, boolean printKernel) { // TODO Override this method for each FPGA backend
        String[] compilationCommand;
        final String inputFile = fpgaSourceDir + entryPoint + METAL_SOURCE_SUFFIX;
        final String outputFile = fpgaSourceDir + entryPoint;
        File fpgaBitStreamFile = new File(outputFile);

        appendSourceToFile(source, entryPoint);

        if (printKernel) {
            RuntimeUtilities.dumpKernel(source);
        }

        String[] commandRename;
        String[] linkCommand = null;
        String[] taskNames;

        taskNames = splitTaskGraphAndTaskName(id);
        if (pendingTasks.containsKey(taskNames[0])) {
            pendingTasks.get(taskNames[0]).add(new Pair(taskNames[1], entryPoint));
        } else {
            ArrayList<Pair> tasks = new ArrayList<>();
            tasks.add(new Pair(taskNames[1], entryPoint));
            pendingTasks.put(taskNames[0], tasks);
        }

        if (isPlatform("xilinx")) {
            compilationCommand = composeXilinxHLSCompileCommand(inputFile, entryPoint);
            linkObjectFiles.add(entryPoint);
            linkCommand = composeXilinxHLSLinkCommand(entryPoint);
        } else if (isPlatform("intel")) {
            if (runOnIntelFPGAWithOneAPI()) {
                if (isQuartusHLSRequired()) {
                    assertIfQuartusHLSIsPresent();
                    compilationCommand = composeIntelHLSCommand(inputFile, outputFile);
                } else {
                    compilationCommand = composeIntelHLSCommandForOneAPI(inputFile, outputFile);
                }
            } else {
                compilationCommand = composeIntelHLSCommand(inputFile, outputFile);
            }
        } else {
            // Should not reach here
            throw new TornadoRuntimeException("[ERROR] FPGA vendor not supported yet.");
        }

        String vendor = getDeviceVendor();

        commandRename = new String[] { FPGA_CLEANUP_SCRIPT, vendor, fpgaSourceDir, entryPoint };
        Path path = Paths.get(outputFile);
        addNewEntryInBitstreamHashMap(id, outputFile);
        if (fpgaBitStreamFile.exists()) {
            return installEntryPointForBinaryForFPGAs(id, path, entryPoint);
        } else {
            invokeShellCommand(compilationCommand);
            invokeShellCommand(commandRename);
            invokeShellCommand(linkCommand);
            if (isFPGAInAWS) {
                String[] afiAWSCommand = new String[] { FPGA_AWS_AFI_SCRIPT, resolveFPGAConfigurationFileName(), directoryBitstream, entryPoint };
                invokeShellCommand(afiAWSCommand);
            }
        }
        return installEntryPointForBinaryForFPGAs(id, path, entryPoint);
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
        file = new File(outDir + "/" + identifier + METAL_SOURCE_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(source);
        } catch (IOException e) {
            logger.error("unable to write error log: ", e.getMessage());
        }

    }

    private void installCodeInCodeCache(MetalProgram program, String id, String entryPoint, MetalInstalledCode code) {
        cache.put(id + "-" + entryPoint, code);
        // BUG Apple does not seem to like implementing the Metal spec
        // properly, this causes a SIGFAULT.
        if ((METAL_CACHE_ENABLE || METAL_DUMP_BINS) && !deviceContext.getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
            final Path outDir = resolveCacheDirectory();
            program.dumpBinaries(outDir.toAbsolutePath() + "/" + entryPoint);
        }
    }

    public MetalInstalledCode installSource(TaskDataContext meta, String id, String entryPoint, byte[] source) {

        logger.info("Installing code for %s into code cache", entryPoint);

        boolean isSPIRVBinary = isInputSourceSPIRVBinary(source);
        final MetalProgram program;
        if (isSPIRVBinary) {
            program = deviceContext.createProgramWithIL(source, new long[] { source.length });
        } else {
            program = deviceContext.createProgramWithSource(source, new long[] { source.length });
        }

        if (METAL_DUMP_SOURCE) {
            final Path outDir = resolveSourceDirectory();
            File file = new File(outDir + "/" + id + "-" + entryPoint + METAL_SOURCE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(source);
            } catch (IOException e) {
                logger.error("unable to dump source: ", e.getMessage());
            }
        }

        if (deviceContext.getDevice().getDeviceType() == MetalDeviceType.CL_DEVICE_TYPE_ACCELERATOR) {
            appendSourceToFile(source, entryPoint);
        }

        if (meta.isPrintKernelEnabled()) {
            RuntimeUtilities.dumpKernel(source);
        }
        logger.debug("\tMetal compiler flags = %s", meta.getCompilerFlags(TornadoVMBackendType.METAL));
        program.build(meta.getCompilerFlags(TornadoVMBackendType.METAL));
        final MetalBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        System.out.println("DEBUG: Metal compilation status = " + status.toString());
        logger.debug("\tMetal compilation status = %s", status.toString());

        if (status == MetalBuildStatus.CL_BUILD_ERROR) {
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            System.err.println("\n[ERROR] TornadoVM JIT Compiler - Metal Build Error Log:\n\n" + log + "\n");
            dumpKernelSource(id, entryPoint, log, source);
            throw new TornadoBailoutRuntimeException("Error during code compilation with the Metal driver");
        }

        MetalKernel kernel = null;
        if (status == CL_BUILD_SUCCESS) {
            kernel = program.clCreateKernel(entryPoint);
            System.out.println("DEBUG: clCreateKernel returned kernel=" + kernel + " for entryPoint=" + entryPoint);
            kernelAvailable = true;
        } else {
            System.out.println("DEBUG: Kernel not created - status is " + status + " (not CL_BUILD_SUCCESS)");
            final String log = program.getBuildLog(deviceContext.getDeviceId());
            System.out.println("DEBUG: Build log: " + log);
        }

        final MetalInstalledCode code = new MetalInstalledCode(entryPoint, source, (MetalDeviceContext) deviceContext, program, kernel, isSPIRVBinary);
        if (status == CL_BUILD_SUCCESS) {
            logger.debug("\tMetal Kernel id = 0x%x", kernel.getOclKernelID());
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

        MetalProgram program;
        boolean isSPIRVBinary = false;
        MetalBuildStatus status = CL_BUILD_SUCCESS;
        if (shouldReuseProgramObject(entryPoint) && cache.containsKey(entryPoint)) {
            program = cache.get(entryPoint).getProgram();
        } else {
            long beforeLoad = (TornadoOptions.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();
            isSPIRVBinary = isInputSourceSPIRVBinary(binary);
            if (isSPIRVBinary) {
                program = deviceContext.createProgramWithIL(binary, new long[] { binary.length });
            } else {
                program = deviceContext.createProgramWithBinary(binary, new long[] { binary.length });
            }
            long afterLoad = (TornadoOptions.TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();

            if (PRINT_LOAD_TIME) {
                System.out.println("Binary load time: " + (afterLoad - beforeLoad) + (TornadoOptions.TIME_IN_NANOSECONDS ? " ns" : " ms") + "\n");
            }

            if (program == null) {
                throw new MetalException("unable to load binary for " + entryPoint);
            }

            program.build("");

            status = program.getStatus(deviceContext.getDeviceId());
            logger.debug("\tMetal compilation status = %s", status.toString());

            final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
            if (!log.isEmpty()) {
                logger.debug(log);
            }
        }

        final MetalKernel kernel = (status == CL_BUILD_SUCCESS) ? program.clCreateKernel(entryPoint) : null;
        final MetalInstalledCode code = new MetalInstalledCode(entryPoint, binary, (MetalDeviceContext) deviceContext, program, kernel, isSPIRVBinary);

        if (status == CL_BUILD_SUCCESS) {
            logger.debug("\tMetal Kernel id = 0x%x", kernel.getOclKernelID());
            cache.put(entryPoint, code);

            String taskScheduleName = splitTaskGraphAndTaskName(id)[0];
            if (pendingTasks.containsKey(taskScheduleName)) {
                ArrayList<Pair> pendingKernels = pendingTasks.get(taskScheduleName);
                for (Pair pair : pendingKernels) {
                    String childKernelName = pair.entryPoint;
                    if (!childKernelName.equals(entryPoint)) {
                        final MetalKernel kernel2 = program.clCreateKernel(childKernelName);
                        final MetalInstalledCode code2 = new MetalInstalledCode(entryPoint, binary, (MetalDeviceContext) deviceContext, program, kernel2, isSPIRVBinary);
                        cache.put(taskScheduleName + "." + pair.taskName + "-" + childKernelName, code2);
                    }
                }
                pendingKernels.clear();
            }

            if ((METAL_CACHE_ENABLE || METAL_DUMP_BINS)) {
                final Path outDir = resolveCacheDirectory();
                RuntimeUtilities.writeToFile(outDir.toAbsolutePath().toString() + "/" + entryPoint, binary);
            }
        } else {
            logger.warn("\tunable to install binary for %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    private boolean shouldReuseProgramObject(String entryPoint) {
        return deviceContext.getDevice().getDeviceName().toLowerCase().startsWith("xilinx");
    }

    public void reset() {
        for (MetalInstalledCode code : cache.values()) {
            code.invalidate();
        }
        cache.clear();
    }

    public MetalInstalledCode installEntryPointForBinaryForFPGAs(String id, Path lookupPath, String entrypoint) {
        final File file = lookupPath.toFile();
        MetalInstalledCode lookupCode = null;
        if (file.length() == 0) {
            logger.error("Empty input binary: %s", file);
        }
        try {
            final byte[] binary = Files.readAllBytes(lookupPath);
            lookupCode = installBinary(id, entrypoint, binary);
        } catch (MetalException | IOException e) {
            logger.error("unable to load binary: %s (%s)", file, e.getMessage());
        }
        return lookupCode;
    }

    public boolean isCached(String key) {
        return cache.containsKey(key);
    }

    public MetalInstalledCode getInstalledCode(String id, String entryPoint) {
        return cache.get(id + "-" + entryPoint);
    }

    private static class Pair {
        private String taskName;
        private String entryPoint;

        public Pair(String id, String entryPoint) {
            this.taskName = id;
            this.entryPoint = entryPoint;
        }
    }
}
