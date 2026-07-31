/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.graal.backend;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CUDA C preamble prepended to compiled kernels that need it.
 *
 * <p>The code generator emits native CUDA C / NVRTC types and intrinsics
 * directly (native unsigned types, inline relational operators, {@code fmin}/
 * {@code fmax}-based clamp, inline radians/sign, real {@code atomic*}
 * intrinsics, and componentwise vector expressions). The half-precision header
 * is only injected when the emitted kernel actually references fp16 constructs
 * ({@code __half}, {@code half2}, {@code __float2half}); the generated source
 * is scanned in {@code CUDACompilationResultBuilder#finish}. Keeping the
 * include conditional limits the blast radius on toolkits whose on-disk
 * {@code cuda_fp16.hpp} does not compile under NVRTC (it references
 * {@code NV_IF_ELSE_TARGET} from {@code <nv/target>}, which is excluded when
 * {@code __CUDACC_RTC__} is defined): only kernels that genuinely need fp16
 * depend on the header resolving. How the include is resolved at compile time
 * (NVRTC built-ins first, toolkit include paths as a fallback) is handled in
 * the JNI layer ({@code CUDAProgram.cpp#compile_with_nvrtc}).
 *
 * <p>Note: DP4A is emitted as inline PTX ({@code dp4a.s32.s32}) directly at the
 * call site (see {@code CUDALIRStmt.Dp4aStmt}), so it needs no preamble helper.
 */
public final class CUDAPreamble {

    private CUDAPreamble() {
    }

    // @formatter:off
    public static final String PREAMBLE =
        "#include <cuda_fp16.h>\n";

    /**
     * FP8 header, injected (after the fp16 include - cuda_fp8.h builds on
     * cuda_fp16.h's __half_raw) only when the kernel references cuda_fp8.h
     * constructs, with the same source-scan gating as PREAMBLE.
     */
    public static final String FP8_PREAMBLE =
        "#include <cuda_fp8.h>\n";
    // @formatter:on

    /**
     * Struct + {@code make_<type>8(...)} constructor definitions for the width-8
     * vector kinds. CUDA has no built-in vector type wider than 4 lanes (unlike
     * OpenCL, which natively supports width 8/16), so {@link
     * uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind#getCUDATypeName()}
     * maps width-8 kinds to one of these instead. Fields are named {@code s0..s7}
     * to match the lane names TornadoVM's CUDA codegen already emits for width-8
     * component access/store (mirroring OpenCL's {@code .sN} swizzle and the
     * {@code getS0()..getS7()} accessors on the Java-side vector types).
     *
     * <p>Each entry is only prepended when the generated kernel source actually
     * references that struct name, with the same source-scan gating as PREAMBLE /
     * FP8_PREAMBLE (see {@code CUDACompilationResultBuilder#finish}). The struct
     * itself never needs a specific memory alignment: TornadoVM's CUDA backend
     * never reinterpret-casts a pointer to a vector struct type (that would fault
     * on element-aligned buffers - see the alignment comments on
     * {@code CUDALIRStmt.VectorLoadStmt}/{@code VectorStoreStmt}); it only ever
     * appears as a register-resident value built and torn down componentwise.
     */
    // @formatter:off
    public static final Map<String, String> WIDTH8_PREAMBLES = buildWidth8Preambles();

    private static Map<String, String> buildWidth8Preambles() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("short8", struct8("short8", "short", "make_short8"));
        map.put("int8", struct8("int8", "int", "make_int8"));
        map.put("float8", struct8("float8", "float", "make_float8"));
        map.put("char8", struct8("char8", "char", "make_char8"));
        map.put("double8", struct8("double8", "double", "make_double8"));
        // __half8 depends on the __half type from cuda_fp16.h (PREAMBLE), which
        // CUDACompilationResultBuilder#finish is ordered to inject first. Constructor
        // is "make_half8" (not "make___half8") to match the existing make_half2/
        // make_half4 naming convention used for the other half-vector widths.
        map.put("__half8", struct8("__half8", "__half", "make_half8"));
        return map;
    }

    private static String struct8(String structName, String elementType, String ctorName) {
        StringBuilder params = new StringBuilder();
        StringBuilder assigns = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                params.append(", ");
            }
            params.append(elementType).append(" s").append(i);
            assigns.append("r.s").append(i).append("=s").append(i).append(";");
        }
        return "struct " + structName + " { " + elementType + " s0,s1,s2,s3,s4,s5,s6,s7; };\n"
                + "static __device__ __forceinline__ " + structName + " " + ctorName + "(" + params + ") {\n"
                + "    " + structName + " r; " + assigns + " return r;\n"
                + "}\n";
    }
    // @formatter:on
}
