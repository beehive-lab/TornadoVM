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
 * be injected ahead of every kernel is the half-precision header plus a small
 * {@code __dp4a} helper.
 *
 * <p>The {@code __dp4a} (4-way signed int8 dot-product-accumulate) helper is
 * emitted as inline PTX rather than relying on the {@code <sm_61_intrinsics.h>}
 * builtin: NVRTC knows the intrinsic prototype but does not pull in its inline
 * definition, so a direct {@code __dp4a(...)} call leaves an unresolved extern
 * function that ptxas rejects with CUDA_ERROR_INVALID_PTX. The inline-PTX
 * helper is self-contained and only assembles on the targeted device (which, by
 * definition, must support DP4A for the kernel to be requested). An unused
 * {@code static __forceinline__} helper produces no PTX, so this is free for
 * kernels that do not use it.
 */
public final class CUDAPreamble {

    private CUDAPreamble() {
    }

    // @formatter:off
    public static final String PREAMBLE =
        "#include <cuda_fp16.h>\n" +
        "static __device__ __forceinline__ int __tornado_dp4a(int a, int b, int c) {\n" +
        "  int d;\n" +
        "  asm(\"dp4a.s32.s32 %0, %1, %2, %3;\" : \"=r\"(d) : \"r\"(a), \"r\"(b), \"r\"(c));\n" +
        "  return d;\n" +
        "}\n";
    // @formatter:on
}
