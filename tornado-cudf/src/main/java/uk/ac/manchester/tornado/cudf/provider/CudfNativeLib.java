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
package uk.ac.manchester.tornado.cudf.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * FFM bindings to {@code libtornado-cudf.so}, the {@code extern "C"} shim over cuDF.
 *
 * <p>
 * Every entry point takes the stream to run on, device pointers for its operands, and returns a
 * status: zero for success, non-zero for a cuDF or CUDA error the shim caught. Nothing here
 * allocates or frees device memory -- TornadoVM owns every buffer involved, which is what lets a
 * cuDF task sit in the same task graph as a generated kernel and read what it wrote.
 */
final class CudfNativeLib {

    /**
     * The shim, not cuDF itself.
     *
     * <p>
     * Built by {@code tornado-drivers/cudf-jni} when RAPIDS libcudf is installed and skipped when
     * it is not, so it is absent on any machine without libcudf. That is not an error:
     * {@link #isAvailable()} reports it and the provider declines. Loading libcudf directly would
     * not help -- it exports mangled C++ symbols returning {@code std::unique_ptr}, which FFM
     * cannot call.
     *
     * <p>
     * Looked up with {@link FFMSupport#loadBundledLibrary}, not {@code loadLibrary}: this is a
     * library the SDK ships in its own {@code lib/} rather than one the system provides, so it is
     * on {@code java.library.path} and not on the loader's path.
     */
    private static final SymbolLookup LIBTORNADO_CUDF = FFMSupport.loadBundledLibrary("libtornado-cudf.so", "tornado-cudf.dll", "libtornado-cudf.dylib");

    private static final MethodHandle SORTED_ORDER;
    private static final MethodHandle GROUP_SUM;
    private static final MethodHandle RUNNING_SUM;
    private static final MethodHandle INNER_JOIN;
    private static final MethodHandle LAST_ERROR;

    static {
        MethodHandle sortedOrder = null;
        MethodHandle groupSum = null;
        MethodHandle runningSum = null;
        MethodHandle innerJoin = null;
        MethodHandle lastError = null;
        if (LIBTORNADO_CUDF != null) {
            // int (*)(void* stream, const int* keys, int n, int* outOrder)
            sortedOrder = FFMSupport.downcall(LIBTORNADO_CUDF, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG), "tornado_cudf_sorted_order");
            groupSum = FFMSupport.downcall(LIBTORNADO_CUDF, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT, C_LONG, C_LONG, C_LONG), "tornado_cudf_group_sum");
            runningSum = FFMSupport.downcall(LIBTORNADO_CUDF, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG), "tornado_cudf_running_sum");
            innerJoin = FFMSupport.downcall(LIBTORNADO_CUDF, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_INT, C_INT, C_LONG, C_LONG, C_LONG), "tornado_cudf_inner_join");
            lastError = FFMSupport.downcall(LIBTORNADO_CUDF, FunctionDescriptor.of(C_POINTER), "tornado_cudf_last_error");
        }
        SORTED_ORDER = sortedOrder;
        GROUP_SUM = groupSum;
        RUNNING_SUM = runningSum;
        INNER_JOIN = innerJoin;
        LAST_ERROR = lastError;
    }

    private CudfNativeLib() {
    }

    /** Whether the shim is present and exports everything this module calls. */
    static boolean isAvailable() {
        return LIBTORNADO_CUDF != null && SORTED_ORDER != null && GROUP_SUM != null && RUNNING_SUM != null && INNER_JOIN != null;
    }

    static void load() {
        if (!isAvailable()) {
            throw new TornadoRuntimeException("[ERROR] Unable to load the cuDF shim. Build libtornado-cudf.so against RAPIDS libcudf -- see tornado-cudf/README.md -- and put it on the library path.");
        }
    }

    /**
     * Turns a non-zero status into an exception carrying what the shim recorded.
     *
     * <p>
     * cuDF reports failure by throwing, so the shim catches and stores the message rather than
     * letting a C++ exception cross the ABI boundary, where it would abort the JVM.
     */
    static void checkStatus(int status, String what) {
        if (status == 0) {
            return;
        }
        String detail = "";
        if (LAST_ERROR != null) {
            try {
                detail = ": " + FFMSupport.readCString((java.lang.foreign.MemorySegment) LAST_ERROR.invokeExact());
            } catch (Throwable ignored) {
                detail = "";
            }
        }
        throw new TornadoRuntimeException("[ERROR] cuDF " + what + " failed with status " + status + detail);
    }

    static int sortedOrder(long stream, long keys, int n, long outOrder) {
        try {
            return (int) SORTED_ORDER.invokeExact(stream, keys, n, outOrder);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] cuDF sortedOrder: " + t.getMessage());
        }
    }

    static int groupSum(long stream, long keys, long values, int n, long outKeys, long outSums, long outGroups) {
        try {
            return (int) GROUP_SUM.invokeExact(stream, keys, values, n, outKeys, outSums, outGroups);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] cuDF groupSum: " + t.getMessage());
        }
    }

    static int runningSum(long stream, long values, int n, long out) {
        try {
            return (int) RUNNING_SUM.invokeExact(stream, values, n, out);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] cuDF runningSum: " + t.getMessage());
        }
    }

    static int innerJoin(long stream, long leftKeys, int leftCount, long rightKeys, int rightCount, int capacity, long outLeft, long outRight, long outCount) {
        try {
            return (int) INNER_JOIN.invokeExact(stream, leftKeys, leftCount, rightKeys, rightCount, capacity, outLeft, outRight, outCount);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] cuDF innerJoin: " + t.getMessage());
        }
    }
}
