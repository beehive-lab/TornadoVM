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
 * CUDA C preamble emitted once before every compiled kernel.
 *
 * <p>The code generator emits native CUDA C / NVRTC types and intrinsics
 * directly (native unsigned types, inline relational operators, {@code fmin}/
 * {@code fmax}-based clamp, inline radians/sign, real {@code atomic*}
 * intrinsics, and componentwise vector expressions). The only thing that must
 * be injected ahead of every kernel is the half-precision header, so the
 * base preamble is reduced to a single {@code #include <cuda_fp16.h>}.
 *
 * <p>The CUB warp-reduce header is kept separate ({@link #CUB_REDUCE_INCLUDE})
 * and emitted only for kernels that actually compose a {@code cub::WarpReduce}
 * (see {@code CUDABackend.emitPrologue}). It must NOT be part of the base
 * preamble: {@code <cub/warp/warp_reduce.cuh>} transitively pulls in host C++
 * standard headers (e.g. {@code <utility>}) that NVRTC cannot resolve, so
 * including it in every kernel breaks runtime compilation of all kernels — even
 * those without any reduction.
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

    // Targeted CUB include for warp-composed reductions, emitted only when a
    // kernel uses cub::WarpReduce (the <cub/cub.cuh> umbrella is likewise NOT
    // NVRTC-compilable). Even this targeted header drags in host std headers on
    // some toolkits, so it is gated on actual usage.
    public static final String CUB_REDUCE_INCLUDE =
        "#include <cub/warp/warp_reduce.cuh>\n";
    // @formatter:on
}
