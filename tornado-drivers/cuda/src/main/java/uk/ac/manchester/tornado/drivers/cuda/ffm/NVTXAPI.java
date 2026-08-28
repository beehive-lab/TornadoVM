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
package uk.ac.manchester.tornado.drivers.cuda.ffm;

import static uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.drivers.cuda.ffm.FFMSupport.downcall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Panama bindings for the NVTX ranges the backend publishes so that Nsight Systems can attribute
 * time to task graphs, library tasks and individual kernels.
 *
 * <p>
 * NVTX v3 is header-only and dispatches through an injection library at run time, which is not
 * something a Panama binding can reproduce; the ABI-stable entry points live in
 * {@code libnvToolsExt}, the compatibility library the CUDA toolkit still ships. When that library
 * is absent every method here is a no-op, exactly as an NVTX build with no profiler attached is.
 */
public final class NVTXAPI {

    private static final SymbolLookup LIBNVTX = FFMSupport.loadLibrary("libnvToolsExt.so.1", "libnvToolsExt.so", "nvToolsExt64_1.dll", "libnvToolsExt.dylib");

    private static final MethodHandle NVTX_RANGE_PUSH_A;
    private static final MethodHandle NVTX_RANGE_POP;
    private static final MethodHandle NVTX_NAME_CU_STREAM_A;

    static {
        if (LIBNVTX == null) {
            NVTX_RANGE_PUSH_A = null;
            NVTX_RANGE_POP = null;
            NVTX_NAME_CU_STREAM_A = null;
        } else {
            NVTX_RANGE_PUSH_A = downcall(LIBNVTX, FunctionDescriptor.of(C_INT, C_POINTER), "nvtxRangePushA");
            NVTX_RANGE_POP = downcall(LIBNVTX, FunctionDescriptor.of(C_INT), "nvtxRangePop");
            NVTX_NAME_CU_STREAM_A = downcall(LIBNVTX, FunctionDescriptor.ofVoid(C_LONG, C_POINTER), "nvtxNameCuStreamA");
        }
    }

    private NVTXAPI() {
    }

    /** Whether NVTX is present; when false all the calls below do nothing. */
    public static boolean isAvailable() {
        return NVTX_RANGE_PUSH_A != null;
    }

    public static void rangePush(String name) {
        if (NVTX_RANGE_PUSH_A == null || name == null) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = FFMSupport.allocateCString(arena, name);
            int ignored = (int) NVTX_RANGE_PUSH_A.invokeExact(text);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void rangePop() {
        if (NVTX_RANGE_POP == null) {
            return;
        }
        try {
            int ignored = (int) NVTX_RANGE_POP.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void nameStream(long stream, String name) {
        if (NVTX_NAME_CU_STREAM_A == null || name == null || stream == 0) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = FFMSupport.allocateCString(arena, name);
            NVTX_NAME_CU_STREAM_A.invokeExact(stream, text);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
