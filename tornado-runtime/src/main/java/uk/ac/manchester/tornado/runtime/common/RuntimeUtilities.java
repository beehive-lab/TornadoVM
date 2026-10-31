/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.options.ProfilerFlags;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.utils.TornadoAPIUtils;

public final class RuntimeUtilities {

    public static final int ONE_GIGABYTE = 1 * 1024 * 1024 * 1024;
    public static final int ONE_MEGABYTE = 1 * 1024 * 1024;
    public static final int ONE_KILOBYTE = 1 * 1024;

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
     * A byte count in human-readable form; see {@link TornadoAPIUtils#humanReadableByteCount}.
     *
     * @param bytes
     *     number of bytes
     * @param si
     *     units (true if 1000, false if 1024).
     * @return the formatted size
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        return TornadoAPIUtils.humanReadableByteCount(bytes, si);
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

    public static void dumpKernel(byte[] source) {
        String sourceCode = new String(source);
        if (DebugFlags.PRINT_KERNEL_DIR.get().isEmpty()) {
            System.out.println(sourceCode);
        } else {
            File fileLog = new File(DebugFlags.PRINT_KERNEL_DIR.get());
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

    public static void profilerFileWriter(String jsonProfile) {
        try (FileWriter fileWriter = new FileWriter(ProfilerFlags.PROFILER_DUMP_DIR.get(), true)) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(jsonProfile);
        } catch (IOException e) {
            throw new TornadoRuntimeException("JSon profiler file cannot be append");
        }
    }

    public static void writeBytecodeToFile(StringBuilder logBuilder) {
        if (logBuilder == null) {
            return; // nothing to dump
        }

        String filePath = getFilePath();
        try (FileWriter fw = new FileWriter(filePath, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
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

        if (DebugFlags.DUMP_BYTECODES_DIR.get() != null && !DebugFlags.DUMP_BYTECODES_DIR.get().isEmpty()) {
            // Create directory if it doesn't exist
            File directory = new File(DebugFlags.DUMP_BYTECODES_DIR.get());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            filePath = DebugFlags.DUMP_BYTECODES_DIR.get() + File.separator + BYTECODES_FILENAME;
        } else {
            filePath = BYTECODES_FILENAME;
        }
        return filePath;
    }
}
