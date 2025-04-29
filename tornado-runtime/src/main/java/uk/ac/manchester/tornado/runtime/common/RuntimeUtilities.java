/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.PRINT_SOURCE_DIRECTORY;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.BasicInductionVariable;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;

public final class RuntimeUtilities {

    public static final int ONE_GIGABYTE = 1 * 1024 * 1024 * 1024;
    public static final int ONE_MEGABYTE = 1 * 1024 * 1024;
    public static final int ONE_KILOBYTE = 1 * 1024;

    public static final String FPGA_OUTPUT_FILENAME = "outputFPGA.log";
    public static final String FPGA_ERROR_FILENAME = "errorFPGA.log";
    public static final String BYTECODES_FILENAME = "tornadovm_bytecodes.log";

    private RuntimeUtilities() {
    }

    public static long parseSize(String size) {
        if (size.endsWith("B")) {
            int index = size.indexOf("B");
            final String prefixes = "KMGTPE";
            if (prefixes.contains(size.substring(index - 1, index))) {
                final int prefix = prefixes.indexOf(size.charAt(index - 1));
                final long base = 1024;
                final long unit = (long) Math.pow(base, prefix + 1);
                return Long.parseLong(size.substring(0, index - 1)) * unit;

            } else {
                return Long.parseLong(size.substring(0, index - 1));
            }
        } else {
            return Long.parseLong(size);
        }
    }

    /**
     * Convert byte sizes into human-readable format Based on code from.
     *
     * @param bytes
     * @param si
     * @return humanReadableByteCount
     * @see <a href=http://stackoverflow.com/questions/3758606/how-to-convert
     *     -byte-size-into-human-readable-format-in-java >Reference to
     *     StackOverflow</a>
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String humanReadableFreq(int freq) {
        final int unit = 1000;
        if (freq < unit) {
            return freq + " MHz";
        }
        final int exp = (int) (Math.log(freq) / Math.log(unit));
        final char pre = "GT".charAt(exp - 1);
        return String.format("%.1f %sHz", freq / Math.pow(unit, exp), pre);
    }

    public static String formatBytes(final long bytes) {
        String out = "";

        if (bytes >= ONE_GIGABYTE) {
            out = String.format("%.2f GB", ((double) bytes / (double) ONE_GIGABYTE));
        } else if (bytes >= ONE_MEGABYTE) {
            out = String.format("%.2f MB", ((double) bytes / (double) ONE_MEGABYTE));
        } else if (bytes >= ONE_KILOBYTE) {
            out = String.format("%.2f KB", ((double) bytes / (double) ONE_KILOBYTE));
        } else {
            out = String.format("%d B", bytes);
        }
        return out;
    }

    public static String formatBytesPerSecond(final double bytes) {
        String out = "";

        if (bytes >= ONE_GIGABYTE) {
            out = String.format("%.2f GB/s", (bytes / ONE_GIGABYTE));
        } else if (bytes >= ONE_MEGABYTE) {
            out = String.format("%.2f MB/s", (bytes / ONE_MEGABYTE));
        } else if (bytes >= ONE_KILOBYTE) {
            out = String.format("%.2f KB/s", (bytes / ONE_KILOBYTE));
        } else {
            out = String.format("%f B/s", bytes);
        }
        return out;
    }

    /**
     * Returns true if object is a boxed type.
     *
     * @param obj
     * @return
     */
    public static boolean isBoxedPrimitive(final Object obj) {
        boolean isBox = false;

        if (obj instanceof Boolean) {
            isBox = true;
        } else if (obj instanceof Byte) {
            isBox = true;
        } else if (obj instanceof Character) {
            isBox = true;
        } else if (obj instanceof Short) {
            isBox = true;
        } else if (obj instanceof HalfFloat) {
            isBox = true;
        } else if (obj instanceof Integer) {
            isBox = true;
        } else if (obj instanceof Long) {
            isBox = true;
        } else if (obj instanceof Float) {
            isBox = true;
        } else if (obj instanceof Double) {
            isBox = true;
        }

        return isBox;
    }

    /**
     * Returns true if object is a boxed type.
     *
     * @param klass
     *     Class to check is boxed type.
     * @return boolean
     */
    public static boolean isBoxedPrimitiveClass(final Class<?> klass) {
        boolean isBox = false;

        if (klass == Boolean.class) {
            isBox = true;
        } else if (klass == Byte.class) {
            isBox = true;
        } else if (klass == Character.class) {
            isBox = true;
        } else if (klass == Short.class) {
            isBox = true;
        } else if (klass == HalfFloat.class) {
            isBox = true;
        } else if (klass == Integer.class) {
            isBox = true;
        } else if (klass == Long.class) {
            isBox = true;
        } else if (klass == Float.class) {
            isBox = true;
        } else if (klass == Double.class) {
            isBox = true;
        }

        return isBox;
    }

    /**
     * Returns true if object is a boxed type.
     *
     * @param clazz
     * @return
     */
    public static Class<?> toUnboxedPrimitiveClass(final Class<?> clazz) {
        Class<?> result = null;

        if (clazz == Boolean.class) {
            result = boolean.class;
        } else if (clazz == Byte.class) {
            result = byte.class;
        } else if (clazz == Character.class) {
            result = char.class;
        } else if (clazz == Short.class) {
            result = short.class;
        } else if (clazz == Integer.class) {
            result = int.class;
        } else if (clazz == Long.class) {
            result = long.class;
        } else if (clazz == Float.class) {
            result = float.class;
        } else if (clazz == Double.class) {
            result = double.class;
        }

        return result != null ? result : clazz;
    }

    /**
     * determines whether a given array is composed of primitives or objects.
     *
     * @param type
     *     type to check
     * @return true if the array is composed of a primitive type
     */
    public static boolean isPrimitiveArray(final Class<?> type) {
        Class<?> componentType = type.getComponentType();
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
        }
        return componentType.isPrimitive() || isBoxedPrimitive(componentType);
    }

    public static void printBuffer(final ByteBuffer buffer) {

        System.out.printf("buffer : position=%d, remaining=%d, capacity=%d, limit=%d\n", buffer.position(), buffer.remaining(), buffer.capacity(), buffer.limit());
        System.out.printf("array  : length=%d, offset=%d\n", buffer.array().length, buffer.arrayOffset());
        System.out.printf("%-8s: ", "Index");
        for (int i = 0; i < 8; i++) {
            System.out.printf("%-8d ", i * 4);
        }
        System.out.println();
        System.out.println();

        System.out.printf("");
        for (int i = 0; i < buffer.remaining(); i += 32) {
            System.out.printf("%-8d: ", i);
            for (int j = 0; j < 32; j += 4) {
                for (int k = 0; k < 4; k++) {
                    if ((i + j + k) < buffer.remaining()) {
                        final byte b = buffer.get(i + j + k);
                        System.out.printf("%02x", b);
                    } else {
                        System.out.printf("%2s", "..");
                    }
                }
                System.out.printf(" ");
            }
            System.out.println();
        }
        System.out.println();
    }

    public static void printBuffer(final ByteBuffer buffer, final int start, final int len) {

        System.out.printf("Index : ");
        for (int i = 0; i < 5; i++) {
            System.out.printf(" %8d", i);
        }
        System.out.println();

        System.out.printf("Buffer: ");
        for (int i = 0; i < len; i++) {
            System.out.printf(" %2X", buffer.get(start + i));
        }
        System.out.println();
    }

    public static double elapsedTimeInSeconds(long start, long end) {
        return elapsedTimeInSeconds((end - start));
    }

    public static double elapsedTimeInSeconds(long duration) {
        return duration * 1e-9;
    }

    public static double elapsedTimeInMilliSeconds(long start, long end) {
        return BigDecimal.valueOf((end - start) * 1e-6).setScale(5, RoundingMode.HALF_UP).doubleValue();
    }

    public static double elapsedTimeInMilliSeconds(long time) {
        return BigDecimal.valueOf(time * 1e-6).setScale(5, RoundingMode.HALF_UP).doubleValue();
    }

    public static String formatArray(Object object) {
        String result;
        if (object.getClass().isArray()) {
            int len = Array.getLength(object);
            result = String.format("%s[%d]", object.getClass().getComponentType().getName(), len);
        } else {
            result = object.toString();
        }
        return result;
    }

    public static boolean isPrimitive(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        } else {
            return isBoxedPrimitive(type);
        }
    }

    public static String formatMethod(ResolvedJavaMethod method) {

        Signature sig = method.getSignature();
        JavaKind rt = sig.getReturnKind();
        StringBuilder sb = new StringBuilder();

        sb.append(rt.getJavaName()).append(" ");
        sb.append(method.getName()).append("(");
        for (int i = 0; i < sig.getParameterCount(!method.isStatic()); i++) {
            JavaKind jk = sig.getParameterKind(i);
            sb.append(jk.toString());
            if (i < sig.getParameterCount(!method.isStatic()) - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * It prints the induction variables for counted loops in the given
     * StructuredGraph. This method can be used for post-processing parallel loops
     * with identify information for the induction variables.
     *
     * @param graph
     *     The StructuredGraph to analyze and print induction variables in
     *     the graph.
     */
    private static void printInductionVariables(StructuredGraph graph) {
        final LoopsData data = new TornadoLoopsData(graph);
        data.detectCountedLoops();

        final List<LoopEx> loops = data.outerFirst();

        List<ParallelRangeNode> parRanges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();
        for (LoopEx loop : loops) {
            for (ParallelRangeNode parRange : parRanges) {
                for (Node n : parRange.offset().usages()) {
                    if (loop.getInductionVariables().containsKey(n)) {
                        BasicInductionVariable iv = (BasicInductionVariable) loop.getInductionVariables().get(n);
                        System.out.printf("[%d] parallel loop: %s -> init=%s, cond=%s, stride=%s, op=%s\n", parRange.index(), loop.loopBegin(), parRange.offset().value(), parRange.value(), parRange
                                .stride(), iv.getOp());
                    }
                }
            }
        }
    }

    public static void dumpKernel(byte[] source) {
        String sourceCode = new String(source);
        if (PRINT_SOURCE_DIRECTORY.isEmpty()) {
            System.out.println(sourceCode);
        } else {
            File fileLog = new File(PRINT_SOURCE_DIRECTORY);
            try {
                try (FileWriter file = new FileWriter(fileLog, fileLog.exists())) {
                    file.write(sourceCode);
                    file.write("\n");
                    file.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void systemCall(String[] command, boolean printStandardOutput, String loggingDirectory) throws IOException {
        String stdOutput;
        StringBuilder standardOutput = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        final String lineSeparator = System.lineSeparator();

        TornadoLogger logger = new TornadoLogger();

        try {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            standardOutput.append("Standard output:").append(lineSeparator);
            String fullCommand = Arrays.toString(command);
            standardOutput.append("Command: ").append(fullCommand).append(lineSeparator).append(lineSeparator);
            while ((stdOutput = stdInput.readLine()) != null) {
                standardOutput.append(stdOutput).append(lineSeparator);
            }
            standardOutput.append("--------------------------------------------------------------------\n");

            errorOutput.append("Standard error (if any) of the command (").append(Arrays.toString(command)).append("):\n");
            while ((stdOutput = stdError.readLine()) != null) {
                errorOutput.append(stdOutput).append(lineSeparator);
            }
            errorOutput.append("--------------------------------------------------------------------\n");

            if (printStandardOutput) {
                System.out.println(standardOutput.toString());
                System.out.println(errorOutput.toString());
            }
            writeStringToFile(loggingDirectory + FPGA_OUTPUT_FILENAME, standardOutput.toString(), true);
            writeStringToFile(loggingDirectory + FPGA_ERROR_FILENAME, errorOutput.toString(), true);
        } catch (IOException e) {
            logger.error("Unable to make a native system call.", e);
            throw new IOException(e);
        } catch (Throwable t) {
            logger.error("Unable to make a native system call.", t);
            throw new TornadoRuntimeException(t.getMessage());
        }
    }

    public static void writeStreamToFile(File file, byte[] source, boolean append) {
        try (FileOutputStream fos = (append ? new FileOutputStream(file, true) : new FileOutputStream(file))) {
            fos.write(source);
        } catch (IOException e) {
            new TornadoLogger().error("unable to dump source: ", e.getMessage());
            throw new RuntimeException("unable to dump source: " + e.getMessage());
        }

    }

    public static void writeStringToFile(String filename, String source, boolean append) {
        try (FileWriter fw = new FileWriter(filename, append)) {
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(source);
            bw.flush();
            bw.close();
        } catch (IOException e) {
            new TornadoLogger().error("unable to dump source: ", e.getMessage());
            throw new RuntimeException("unable to dump source: " + e.getMessage());
        }

    }

    public static void writeToFile(String file, byte[] binary) {
        new TornadoLogger().info("dumping binary %s", file);
        try (FileOutputStream fis = new FileOutputStream(file);) {
            fis.write(binary);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getTornadoInstanceIP() {
        String localIP = null;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            localIP = ip.getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println("Exception occurred" + e.getMessage());
        }
        return localIP;
    }

    public static void profilerFileWriter(String jsonProfile) {
        try (FileWriter fileWriter = new FileWriter(TornadoOptions.PROFILER_DIRECTORY, true)) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(jsonProfile);
        } catch (IOException e) {
            throw new TornadoRuntimeException("JSon profiler file cannot be append");
        }
    }

    public static void writeBytecodeToFile(StringBuilder logBuilder) {
        String filePath = getFilePath();
        try (FileWriter fw = new FileWriter(filePath, true)) {
            BufferedWriter bw = new BufferedWriter(fw);
            // Clean ANSI escape sequences before writing
            String cleanedString = removeAnsiEscapeCodes(logBuilder.toString());
            bw.write(cleanedString);
            bw.flush();
        } catch (IOException e) {
            new TornadoLogger().error("unable to dump bytecodes: ", e.getMessage());
            throw new RuntimeException("unable to dump bytecodes: " + e.getMessage());
        }
    }

    /**
     * Removes ANSI escape sequences from the input string.
     *
     * @param input
     *     String potentially containing ANSI escape codes
     * @return String with all ANSI escape sequences removed
     */
    private static String removeAnsiEscapeCodes(String input) {
        // Pattern to match ANSI escape sequences
        // This matches the most common escape codes, beginning with ESC [ and ending with m
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private static String getFilePath() {
        String filePath;

        if (TornadoOptions.DUMP_BYTECODES != null && !TornadoOptions.DUMP_BYTECODES.isEmpty()) {
            // Create directory if it doesn't exist
            File directory = new File(TornadoOptions.DUMP_BYTECODES);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            filePath = TornadoOptions.DUMP_BYTECODES + File.separator + BYTECODES_FILENAME;
        } else {
            filePath = BYTECODES_FILENAME;
        }
        return filePath;
    }
}
