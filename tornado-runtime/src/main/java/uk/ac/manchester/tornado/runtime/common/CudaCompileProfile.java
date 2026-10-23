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

/**
 * Named bundle of NVRTC options, selected with {@code -Dtornado.cuda.compile.profile=<name>}.
 *
 * <p>
 * The CUDA backend already compiles through NVRTC and already threads per-backend compiler flags all the
 * way to {@code CUDACodeCache}, but reaching a useful combination meant knowing which NVRTC spellings to
 * put in {@code tornado.cuda.compiler.flags}. These profiles name the combinations that are actually
 * wanted; {@link #DEFAULT} changes nothing, and flags set explicitly through
 * {@code tornado.cuda.compiler.flags} or {@code withCompilerFlags(CUDA, ...)} are added to the
 * profile's, an explicit option replacing the profile's option of the same name (see
 * {@link CompilerFlags}).
 * </p>
 */
public enum CudaCompileProfile {

    /** Whatever NVRTC does by default: no extra options. */
    DEFAULT(""),

    /** Trade floating-point strictness for speed. */
    FAST("--use_fast_math --extra-device-vectorization"),

    /**
     * Keep line-number information in the cubin so Nsight Systems and Nsight Compute can attribute
     * samples to the generated CUDA C. Without it, kernel-level profiling has to be inferred.
     */
    DEBUG("-lineinfo"),

    /**
     * Reproducible arithmetic: no fast-math and no FMA contraction, so a kernel's result does not depend
     * on how the compiler fused its multiplies and adds.
     */
    REPRO("--fmad=false");

    private final String flags;

    CudaCompileProfile(String flags) {
        this.flags = flags;
    }

    public String getFlags() {
        return flags;
    }

    public static CudaCompileProfile parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase()) {
            case "default", "none" -> DEFAULT;
            case "fast" -> FAST;
            case "debug", "lineinfo" -> DEBUG;
            case "repro", "deterministic" -> REPRO;
            default -> {
                System.err.println("[TornadoVM] unknown tornado.cuda.compile.profile value '" + value + "', using 'default'. " //
                        + "Valid values: default, fast, debug, repro.");
                yield DEFAULT;
            }
        };
    }
}
