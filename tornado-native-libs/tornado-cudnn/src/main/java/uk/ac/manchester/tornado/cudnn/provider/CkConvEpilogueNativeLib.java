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

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Panama binding to the convolution epilogue in libtornado-ck (tornado-native-libs/ck-hip): bias,
 * residual and ReLU applied in place to the NHWC output of a MIOpen convolution, in FP32, on the
 * given stream. MIOpen cannot fuse a residual add, and its element-wise operations take one data
 * type, while the FP16 fused convolution takes an FP32 bias.
 */
final class CkConvEpilogueNativeLib {

    /** Return codes of {@code tornado_ck_conv_epilogue}. */
    static final int SUCCESS = 0;
    static final int NO_KERNEL = 1;
    static final int INVALID_ARGUMENT = 2;
    static final int HIP_FAILURE = 3;

    private static final SymbolLookup LIBTORNADO_CK = FFMSupport.loadBundledLibrary("libtornado-ck.so", "tornado-ck.dll");

    private static final MethodHandle CONV_EPILOGUE = LIBTORNADO_CK == null ? null
            : FFMSupport.downcall(LIBTORNADO_CK, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT), "tornado_ck_conv_epilogue");

    private CkConvEpilogueNativeLib() {
    }

    static boolean isAvailable() {
        return CONV_EPILOGUE != null;
    }

    /**
     * {@code out = relu(out + bias[channel] + residual)} in place on {@code stream}, over an NHWC
     * tensor of {@code total} elements and {@code channels} channels, FP16 when {@code half}. The
     * {@code flags} are those of {@code CuDnn.CONV_BIAS}, {@code CONV_RESIDUAL} and
     * {@code CONV_RELU}; a pointer whose flag is off is not read.
     */
    static int convEpilogue(long stream, boolean half, long out, long bias, long residual, long total, int channels, int flags) {
        try {
            return (int) CONV_EPILOGUE.invokeExact(stream, half ? 1 : 0, out, bias, residual, total, channels, flags);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
