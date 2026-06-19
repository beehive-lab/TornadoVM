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
 * <p>The codegen layer is cloned from the OpenCL C backend, so it emits
 * OpenCL-style scalar type names ({@code uchar}, {@code uint}, {@code ulong},
 * {@code ushort}). CUDA C / NVRTC does not define these as built-in types, so
 * this preamble provides the matching {@code typedef}s. In particular the
 * device-buffer kernel parameters are generated as {@code uchar *}, which would
 * not compile under NVRTC without these aliases.
 *
 * <p>It also provides {@code __device__} implementations of the OpenCL C
 * built-ins that CUDA C / NVRTC does not define ({@code radians}, {@code sign},
 * {@code clamp}, and the scalar relational {@code isequal}/{@code isless}
 * family). These are general, type-templated implementations matching the
 * OpenCL semantics — they are NOT specialised per kernel; they let the
 * unmodified codegen emit calls that resolve to correct CUDA device code.
 */
public final class CUDAPreamble {

    private CUDAPreamble() {
    }

    // @formatter:off
    public static final String PREAMBLE =
        "#include <cuda_fp16.h>\n" +
        "typedef unsigned char uchar;\n" +
        "typedef unsigned short ushort;\n" +
        "typedef unsigned int uint;\n" +
        "typedef unsigned long ulong;\n" +
        // OpenCL C built-ins with no CUDA equivalent. Templated so they apply to
        // float/double (and clamp also to integer kinds) exactly as OpenCL does.
        "template<typename T> __device__ inline T radians(T d) { return d * (T) 0.017453292519943295; }\n" +
        // Java Math.signum semantics: signum(NaN) == NaN (OpenCL sign() returns 0).
        "template<typename T> __device__ inline T sign(T x) { return isnan((double) x) ? x : (x > (T) 0 ? (T) 1 : (x < (T) 0 ? (T) -1 : (T) 0)); }\n" +
        "template<typename T> __device__ inline T clamp(T x, T lo, T hi) { return x < lo ? lo : (x > hi ? hi : x); }\n" +
        "template<typename T> __device__ inline int isequal(T a, T b) { return (int) (a == b); }\n" +
        "template<typename T> __device__ inline int isnotequal(T a, T b) { return (int) (a != b); }\n" +
        "template<typename T> __device__ inline int isgreater(T a, T b) { return (int) (a > b); }\n" +
        "template<typename T> __device__ inline int isgreaterequal(T a, T b) { return (int) (a >= b); }\n" +
        "template<typename T> __device__ inline int isless(T a, T b) { return (int) (a < b); }\n" +
        "template<typename T> __device__ inline int islessequal(T a, T b) { return (int) (a <= b); }\n" +
        "template<typename T> __device__ inline int islessgreater(T a, T b) { return (int) ((a < b) || (a > b)); }\n";
    // @formatter:on
}
