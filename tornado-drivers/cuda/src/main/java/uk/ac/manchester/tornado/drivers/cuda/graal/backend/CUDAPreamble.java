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
}
