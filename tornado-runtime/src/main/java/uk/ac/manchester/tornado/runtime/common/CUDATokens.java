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
