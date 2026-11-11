/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.hip.ffm;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.downcall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panama bindings for the ROCTX ranges the backend publishes so that rocprof and the ROCm Systems
 * Profiler can attribute time to task graphs, library tasks and individual kernels.
 *
 * <p>
 * The rocprofiler-sdk ROCTX library is preferred, as it can also name streams; the older
 * {@code libroctx64} has ranges only. When neither is present every method here is a no-op, as a
 * ROCTX build with no profiler attached is.
 */
public final class ROCTXAPI {

    private static final SymbolLookup LIBROCTX = ROCmLibraries.load(List.of("librocprofiler-sdk-roctx.so.1", "libroctx64.so.4", "librocprofiler-sdk-roctx.so", "libroctx64.so"),
            List.of("rocprofiler-sdk-roctx.dll", "roctx64.dll"));

    private static final MethodHandle ROCTX_RANGE_PUSH_A;
    private static final MethodHandle ROCTX_RANGE_POP;
    private static final MethodHandle ROCTX_NAME_HIP_STREAM;

    static {
        if (LIBROCTX == null) {
            ROCTX_RANGE_PUSH_A = null;
            ROCTX_RANGE_POP = null;
            ROCTX_NAME_HIP_STREAM = null;
        } else {
            ROCTX_RANGE_PUSH_A = downcall(LIBROCTX, FunctionDescriptor.of(C_INT, C_POINTER), "roctxRangePushA");
            ROCTX_RANGE_POP = downcall(LIBROCTX, FunctionDescriptor.of(C_INT), "roctxRangePop");
            ROCTX_NAME_HIP_STREAM = downcall(LIBROCTX, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "roctxNameHipStream");
        }
    }

    private ROCTXAPI() {
    }

    /** Whether ROCTX is present; when false all the calls below do nothing. */
    public static boolean isAvailable() {
        return ROCTX_RANGE_PUSH_A != null;
    }

    /**
     * Range labels interned as native strings. A range is pushed around every kernel launch and
     * every transfer, so allocating and encoding the label afresh each time would add host cost to
     * the issue path even with no profiler attached. The labels repeat -- kernel names, and
     * transfer labels rounded to a unit -- so they are encoded once and reused. The cache is
     * capped: a workload that somehow produces unbounded distinct labels falls back to allocating
     * per call rather than growing without limit.
     */
    private static final int LABEL_CACHE_LIMIT = 1024;

    private static final Map<String, MemorySegment> LABELS = new ConcurrentHashMap<>();

    public static void rangePush(String name) {
        if (ROCTX_RANGE_PUSH_A == null || name == null) {
            return;
        }
        MemorySegment label = LABELS.get(name);
        if (label != null) {
            push(label);
            return;
        }
        if (LABELS.size() < LABEL_CACHE_LIMIT) {
            label = LABELS.computeIfAbsent(name, key -> FFMSupport.allocateCString(FFMSupport.GLOBAL, key));
            push(label);
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            push(FFMSupport.allocateCString(arena, name));
        }
    }

    private static void push(MemorySegment label) {
        try {
            int ignored = (int) ROCTX_RANGE_PUSH_A.invokeExact(label);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void rangePop() {
        if (ROCTX_RANGE_POP == null) {
            return;
        }
        try {
            int ignored = (int) ROCTX_RANGE_POP.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void nameStream(long stream, String name) {
        if (ROCTX_NAME_HIP_STREAM == null || name == null || stream == 0) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = FFMSupport.allocateCString(arena, name);
            int ignored = (int) ROCTX_NAME_HIP_STREAM.invokeExact(text, stream);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
