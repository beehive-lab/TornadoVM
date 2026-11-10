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
package uk.ac.manchester.tornado.cudnn.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_FLOAT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Panama bindings to libtornado-ck, the fused FP16 attention kernels for HIP devices that TornadoVM
 * builds from AMD Composable Kernel (tornado-native-libs/ck-hip). The kernels need no plan or
 * workspace: each call selects one for the shape and launches it on the given stream, so it may be
 * captured in a graph.
 */
final class CkAttentionNativeLib {

    /** Return codes of {@code tornado_ck_sdpa_forward}. */
    static final int SUCCESS = 0;
    static final int NO_KERNEL = 1;
    static final int INVALID_ARGUMENT = 2;
    static final int HIP_FAILURE = 3;

    private static final SymbolLookup LIBTORNADO_CK = FFMSupport.loadBundledLibrary("libtornado-ck.so", "tornado-ck.dll");

    private static final MethodHandle SDPA_FORWARD = LIBTORNADO_CK == null ? null
            : FFMSupport.downcall(LIBTORNADO_CK, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_FLOAT, C_INT, C_POINTER),
                    "tornado_ck_sdpa_forward");

    /** Scratch for the strides, which the kernel entry point reads by pointer. */
    private static final FFMSupport.Staging STRIDES = new FFMSupport.Staging();

    private CkAttentionNativeLib() {
    }

    static boolean isAvailable() {
        return SDPA_FORWARD != null;
    }

    /**
     * {@code O = softmax(Q K^T * scale [+ causal mask]) V} on {@code stream}, FP16 with FP32
     * accumulation. {@code strides} holds 16 element strides: (batch, head, sequence, dim) of q, k, v
     * and then o; the dim strides must be 1. The causal mask lets query {@code i} see keys
     * {@code 0..i}.
     */
    static int sdpaForward(long stream, long q, long k, long v, long o, int b, int h, int sQ, int sKv, int d, float scale, boolean causal, long[] strides) {
        MemorySegment segment = STRIDES.forBytes((long) strides.length * Long.BYTES);
        for (int i = 0; i < strides.length; i++) {
            segment.setAtIndex(C_LONG, i, strides[i]);
        }
        try {
            return (int) SDPA_FORWARD.invokeExact(stream, q, k, v, o, b, h, sQ, sKv, d, scale, causal ? 1 : 0, segment);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
