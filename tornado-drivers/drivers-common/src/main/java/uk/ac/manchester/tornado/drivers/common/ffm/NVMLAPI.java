/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.common.ffm;

import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.downcall;

import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Panama bindings for the NVIDIA Management Library, which is where the profiler's power readings
 * come from.
 *
 * <p>
 * NVML ships with the GPU driver rather than being linked at build time, and is absent on any
 * non-NVIDIA host, so it is opened by name at run time and every method degrades to
 * {@link #NVML_ERROR_UNINITIALIZED} when it is not there. That mirrors what the JNI build did with
 * its own dlopen-based loader, and is why a machine with no NVIDIA driver simply reports no power
 * rather than failing.
 */
public final class NVMLAPI {

    /** {@code NVML_SUCCESS}. */
    public static final int NVML_SUCCESS = 0;
    /** {@code NVML_ERROR_UNINITIALIZED}, reported when NVML could not be loaded at all. */
    public static final int NVML_ERROR_UNINITIALIZED = 1;

    private static final SymbolLookup LIBNVML = loadNvml();

    private static final MethodHandle NVML_INIT;
    private static final MethodHandle NVML_SHUTDOWN;
    private static final MethodHandle NVML_DEVICE_GET_HANDLE_BY_INDEX;
    private static final MethodHandle NVML_DEVICE_GET_POWER_USAGE;

    static {
        if (LIBNVML == null) {
            NVML_INIT = null;
            NVML_SHUTDOWN = null;
            NVML_DEVICE_GET_HANDLE_BY_INDEX = null;
            NVML_DEVICE_GET_POWER_USAGE = null;
        } else {
            // The NVML headers rename these to versioned symbols; the unversioned names are the
            // fallback for the older driver that only exports those.
            NVML_INIT = downcall(LIBNVML, FunctionDescriptor.of(C_INT), "nvmlInit_v2", "nvmlInit");
            NVML_SHUTDOWN = downcall(LIBNVML, FunctionDescriptor.of(C_INT), "nvmlShutdown");
            NVML_DEVICE_GET_HANDLE_BY_INDEX = downcall(LIBNVML, FunctionDescriptor.of(C_INT, C_INT, C_POINTER), "nvmlDeviceGetHandleByIndex_v2", "nvmlDeviceGetHandleByIndex");
            NVML_DEVICE_GET_POWER_USAGE = downcall(LIBNVML, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvmlDeviceGetPowerUsage");
        }
    }

    private NVMLAPI() {
    }

    private static SymbolLookup loadNvml() {
        List<String> candidates = new ArrayList<>();
        candidates.add("libnvidia-ml.so.1");
        candidates.add("libnvidia-ml.so");
        candidates.add("nvml.dll");
        candidates.add("C:\\Windows\\System32\\nvml.dll");
        String cudaPath = System.getenv("CUDA_PATH");
        if (cudaPath != null && !cudaPath.isEmpty()) {
            candidates.add(new File(new File(cudaPath, "bin"), "nvml.dll").getAbsolutePath());
        }
        return FFMSupport.loadLibrary(candidates.toArray(new String[0]));
    }

    /** Whether NVML is present; when false every call below reports NVML_ERROR_UNINITIALIZED. */
    public static boolean isAvailable() {
        return LIBNVML != null && NVML_INIT != null && NVML_DEVICE_GET_POWER_USAGE != null;
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    public static int nvmlInit() {
        if (NVML_INIT == null) {
            return NVML_ERROR_UNINITIALIZED;
        }
        try {
            return (int) NVML_INIT.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvmlShutdown() {
        if (NVML_SHUTDOWN == null) {
            return NVML_ERROR_UNINITIALIZED;
        }
        try {
            return (int) NVML_SHUTDOWN.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Writes the opaque {@code nvmlDevice_t} for {@code index} into the given pointer slot. */
    public static int nvmlDeviceGetHandleByIndex(int index, MemorySegment device) {
        if (NVML_DEVICE_GET_HANDLE_BY_INDEX == null) {
            return NVML_ERROR_UNINITIALIZED;
        }
        try {
            return (int) NVML_DEVICE_GET_HANDLE_BY_INDEX.invokeExact(index, device);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Writes the device's current draw, in milliwatts, into the given {@code unsigned int} slot. */
    public static int nvmlDeviceGetPowerUsage(long device, MemorySegment milliwatts) {
        if (NVML_DEVICE_GET_POWER_USAGE == null) {
            return NVML_ERROR_UNINITIALIZED;
        }
        try {
            return (int) NVML_DEVICE_GET_POWER_USAGE.invokeExact(device, milliwatts);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
