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
package uk.ac.manchester.tornado.runtime.common;

import static uk.ac.manchester.tornado.runtime.common.Tornado.error;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import sun.misc.Unsafe;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

public class RuntimeUtilities {

    public static final int ONE_GIGABYTE = 1 * 1024 * 1024 * 1024;
    public static final int ONE_MEGABYTE = 1 * 1024 * 1024;
    public static final int ONE_KILOBYTE = 1 * 1024;

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
     * Convert byte sizes into human readable format Based on code from
     * 
     * @see <a href=http://stackoverflow.com/questions/3758606/how-to-convert
     *      -byte-size-into-human-readable-format-in-java >Reference to
     *      StackOverflow</a>
     * 
     *
     * @param bytes
     * @param si
     *
     * @return humanReadableByteCount
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

    public static final String formatBytes(final long bytes) {
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

    public static final String formatBytesPerSecond(final double bytes) {
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
     * Returns true if object is a boxed type
     *
     * @param obj
     *
     * @return
     */
    public static final boolean isBoxedPrimitive(final Object obj) {
        boolean isBox = false;

        if (obj instanceof Boolean) {
            isBox = true;
        } else if (obj instanceof Byte) {
            isBox = true;
        } else if (obj instanceof Character) {
            isBox = true;
        } else if (obj instanceof Short) {
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
     * Returns true if object is a boxed type
     *
     * @param clazz
     *
     * @return
     */
    public static final boolean isBoxedPrimitiveClass(final Class<?> clazz) {
        boolean isBox = false;

        if (clazz == Boolean.class) {
            isBox = true;
        } else if (clazz == Byte.class) {
            isBox = true;
        } else if (clazz == Character.class) {
            isBox = true;
        } else if (clazz == Short.class) {
            isBox = true;
        } else if (clazz == Integer.class) {
            isBox = true;
        } else if (clazz == Long.class) {
            isBox = true;
        } else if (clazz == Float.class) {
            isBox = true;
        } else if (clazz == Double.class) {
            isBox = true;
        }

        return isBox;
    }

    /**
     * Returns true if object is a boxed type
     *
     * @param clazz
     *
     * @return
     */
    public static final Class<?> toUnboxedPrimitiveClass(final Class<?> clazz) {
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
     * determines whether a given array is composed of primitives or objects
     *
     * @param type
     *            type to check
     *
     * @return true if the array is composed of a primitive type
     */
    public static final boolean isPrimitiveArray(final Class<?> type) {
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

    @SuppressWarnings("restriction")
    public static Unsafe getUnsafe() {
        Unsafe result = null;
        try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);

            result = unsafeConstructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static double elapsedTimeInSeconds(long start, long end) {
        final long duration = end - start;
        double elapsed = duration * 1e-9;
        return elapsed;
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

    public static boolean ifFileExists(File fileName) {
        return fileName.exists();
    }

    public static void sysCall(String[] command, boolean printStandardOutput) throws IOException {
        String stdOutput = null;
        StringBuffer standardOutput = new StringBuffer();
        StringBuffer errorOutput = new StringBuffer();

        try {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            standardOutput.append("Here is the standard output of the command:\n");
            while ((stdOutput = stdInput.readLine()) != null) {
                standardOutput.append(stdOutput);
            }
            errorOutput.append("Here is the standard error of the command (if any):\n");
            while ((stdOutput = stdError.readLine()) != null) {
                errorOutput.append(stdOutput);
            }
            if (printStandardOutput) {
                System.out.println(standardOutput.toString());
                System.out.println(errorOutput.toString());
            }

        } catch (IOException e) {
            error("Unable to make a native system call.", e);
            throw new IOException(e);
        } catch (Throwable t) {
            error("Unable to make a native system call.", t);
            throw new TornadoRuntimeException(t.getMessage());
        }
    }

    public static void writeStreamToFile(File file, byte[] source, boolean append) {
        try (FileOutputStream fos = (append ? new FileOutputStream(file, true) : new FileOutputStream(file))) {
            fos.write(source);
        } catch (IOException e) {
            error("unable to dump source: ", e.getMessage());
            throw new RuntimeException("unable to dump source: " + e.getMessage());
        }

    }

    public static void writeToFile(String file, byte[] binary) {
        info("dumping binary %s", file);
        try (FileOutputStream fis = new FileOutputStream(file);) {
            fis.write(binary);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private RuntimeUtilities() {
    }
}
