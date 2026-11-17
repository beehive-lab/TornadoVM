/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.drivers.hip.ffm;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.downcall;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings for the AMD SMI library, the source of the profiler's power readings on AMD GPUs
 * (the counterpart of {@code NVMLAPI}). It is opened at run time and every method degrades to
 * {@link #AMDSMI_STATUS_NOT_LOADED} when it is absent, so a host without it reports no power.
 */
public final class AMDSMIAPI {

    /** {@code AMDSMI_STATUS_SUCCESS}. */
    public static final int AMDSMI_STATUS_SUCCESS = 0;
    /** Reported when the library could not be loaded ({@code AMDSMI_STATUS_NOT_INIT}). */
    public static final int AMDSMI_STATUS_NOT_LOADED = 32;
    /** {@code AMDSMI_INIT_AMD_GPUS}. */
    public static final long AMDSMI_INIT_AMD_GPUS = 1L << 1;

    /** Size of {@code amdsmi_power_info_t}, and the offsets of the fields read from it. */
    public static final long POWER_INFO_BYTES = 192;
    public static final long POWER_INFO_SOCKET_POWER = 0;
    public static final long POWER_INFO_CURRENT_SOCKET_POWER = 8;
    public static final long POWER_INFO_AVERAGE_SOCKET_POWER = 12;
    /** The value of a field the GPU does not report. */
    public static final int UNSUPPORTED = -1;

    private static final SymbolLookup LIBAMDSMI = ROCmLibraries.load(List.of("libamd_smi.so.27", "libamd_smi.so.26", "libamd_smi.so.25", "libamd_smi.so"), List.of());

    private static final MethodHandle AMDSMI_INIT;
    private static final MethodHandle AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF;
    private static final MethodHandle AMDSMI_GET_POWER_INFO;

    static {
        if (LIBAMDSMI == null) {
            AMDSMI_INIT = null;
            AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF = null;
            AMDSMI_GET_POWER_INFO = null;
        } else {
            AMDSMI_INIT = downcall(LIBAMDSMI, FunctionDescriptor.of(C_INT, C_LONG), "amdsmi_init");
            // amdsmi_bdf_t is a 64-bit union passed by value
            AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF = downcall(LIBAMDSMI, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "amdsmi_get_processor_handle_from_bdf");
            AMDSMI_GET_POWER_INFO = downcall(LIBAMDSMI, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "amdsmi_get_power_info");
        }
    }

    private AMDSMIAPI() {
    }

    /** Whether AMD SMI is present; when false every call below reports AMDSMI_STATUS_NOT_LOADED. */
    public static boolean isAvailable() {
        return AMDSMI_INIT != null && AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF != null && AMDSMI_GET_POWER_INFO != null;
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

    public static int amdsmiInit(long flags) {
        if (AMDSMI_INIT == null) {
            return AMDSMI_STATUS_NOT_LOADED;
        }
        try {
            return (int) AMDSMI_INIT.invokeExact(flags);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** The {@code amdsmi_bdf_t} value of a PCI address: function in bits 0-2, device 3-7, bus 8-15, domain above. */
    public static long bdf(int domain, int bus, int device, int function) {
        return ((long) domain << 16) | ((long) (bus & 0xff) << 8) | ((long) (device & 0x1f) << 3) | (function & 0x7);
    }

    /** Writes the processor handle of the GPU at {@code bdf} into the given pointer slot. */
    public static int amdsmiGetProcessorHandleFromBdf(long bdf, MemorySegment handle) {
        if (AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF == null) {
            return AMDSMI_STATUS_NOT_LOADED;
        }
        try {
            return (int) AMDSMI_GET_PROCESSOR_HANDLE_FROM_BDF.invokeExact(bdf, handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Fills the given {@link #POWER_INFO_BYTES}-byte {@code amdsmi_power_info_t}. */
    public static int amdsmiGetPowerInfo(long processor, MemorySegment powerInfo) {
        if (AMDSMI_GET_POWER_INFO == null) {
            return AMDSMI_STATUS_NOT_LOADED;
        }
        try {
            return (int) AMDSMI_GET_POWER_INFO.invokeExact(processor, powerInfo);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
