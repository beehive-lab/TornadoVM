/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.util.HashSet;

/**
 * Reserved CUDA C / NVRTC keywords and built-in identifiers that the CUDA
 * backend must avoid when generating variable names in the kernel source.
 */
public class CUDATokens {
    public static HashSet<String> cudaTokens = new HashSet<>();
    static {
        // Function and storage qualifiers
        cudaTokens.add("__global__");
        cudaTokens.add("__device__");
        cudaTokens.add("__host__");
        cudaTokens.add("__shared__");
        cudaTokens.add("__constant__");
        cudaTokens.add("__restrict__");
        cudaTokens.add("__launch_bounds__");
        cudaTokens.add("__forceinline__");
        cudaTokens.add("extern");

        // Built-in thread/block/grid variables
        cudaTokens.add("threadIdx");
        cudaTokens.add("blockIdx");
        cudaTokens.add("blockDim");
        cudaTokens.add("gridDim");
        cudaTokens.add("warpSize");

        // Synchronization and built-ins
        cudaTokens.add("__syncthreads");
        cudaTokens.add("__syncwarp");
        cudaTokens.add("__threadfence");
        cudaTokens.add("__threadfence_block");
        cudaTokens.add("printf");

        // Common atomics
        cudaTokens.add("atomicAdd");
        cudaTokens.add("atomicSub");
        cudaTokens.add("atomicExch");
        cudaTokens.add("atomicMin");
        cudaTokens.add("atomicMax");
        cudaTokens.add("atomicAnd");
        cudaTokens.add("atomicOr");
        cudaTokens.add("atomicXor");
        cudaTokens.add("atomicCAS");
        cudaTokens.add("atomicInc");
        cudaTokens.add("atomicDec");

        // C++ reserved keywords. NVRTC compiles the kernel source as C++ (the
        // 'extern "C"' only affects linkage, not the language), so a Java
        // parameter/variable whose name collides with a C++ keyword (most
        // commonly the receiver 'this') must be renamed to compile.
        cudaTokens.add("this");
        cudaTokens.add("new");
        cudaTokens.add("delete");
        cudaTokens.add("class");
        cudaTokens.add("struct");
        cudaTokens.add("union");
        cudaTokens.add("template");
        cudaTokens.add("typename");
        cudaTokens.add("namespace");
        cudaTokens.add("operator");
        cudaTokens.add("private");
        cudaTokens.add("public");
        cudaTokens.add("protected");
        cudaTokens.add("virtual");
        cudaTokens.add("friend");
        cudaTokens.add("explicit");
        cudaTokens.add("inline");
        cudaTokens.add("typeid");
        cudaTokens.add("using");
        cudaTokens.add("try");
        cudaTokens.add("catch");
        cudaTokens.add("throw");
        cudaTokens.add("bool");
        cudaTokens.add("true");
        cudaTokens.add("false");
        cudaTokens.add("nullptr");
    }
}
