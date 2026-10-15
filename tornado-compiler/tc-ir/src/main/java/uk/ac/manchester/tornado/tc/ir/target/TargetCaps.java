/*
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.ir.target;

/**
 * What a target device and toolchain support. Immutable and part of every compilation-cache key;
 * built from the driver's device queries (TC never queries devices itself). See
 * {@code backend-contracts.md} section 1.
 *
 * @param dialect             source language the emitter produces
 * @param computeCapability   CUDA compute capability as major*10+minor (89 for sm_89), 0 elsewhere
 * @param fp64                double precision supported
 * @param fp16                half precision supported
 * @param arrayHeaderBytes    bytes before element 0 of a TornadoVM native array (16, or 24 uncompressed)
 * @param maxWorkGroupSize    largest work-group (thread-block) size
 * @param localMemoryBytes    shared/local memory per work-group
 * @param warpSize            SIMD width (warp or sub-group)
 * @param deviceLaunch        kernels can launch kernels (CUDA dynamic parallelism)
 * @param nvrtcVersion        the NVRTC that compiles CUDA kernels as major*1000+minor (12004 for 12.4); 0 when
 *                            unknown or not CUDA. NVRTC stamps the PTX with its own ISA version, so an
 *                            instruction newer than the toolkit cannot be expressed on any GPU.
 */
public record TargetCaps(Dialect dialect, int computeCapability, boolean fp64, boolean fp16, int arrayHeaderBytes, int maxWorkGroupSize, int localMemoryBytes, int warpSize,
        boolean deviceLaunch, int nvrtcVersion) {

    public enum Dialect {
        CUDA, OPENCL, METAL
    }

    /** NVIDIA GeForce RTX 4090 through NVRTC: the first parity target. */
    public static final TargetCaps CUDA_SM89 = new TargetCaps(Dialect.CUDA, 89, true, true, 16, 1024, 48 * 1024, 32, true, 0);

    /**
     * OpenCL C 1.2 with doubles and halves, for headless use (tests, tc-emit); the OpenCL driver
     * builds the real values from its device queries. No warp width is assumed (sub-groups vary by
     * vendor) and kernels cannot launch kernels.
     */
    public static final TargetCaps OPENCL_GENERIC = new TargetCaps(Dialect.OPENCL, 0, true, true, 16, 1024, 48 * 1024, 0, false, 0);

    /**
     * An Apple-silicon GPU through Metal, for headless use: no double precision, halves, 32-wide SIMD
     * groups, 1024 threads and 32 KB of threadgroup memory per work-group; no device-side launches.
     */
    public static final TargetCaps METAL_APPLE = new TargetCaps(Dialect.METAL, 0, false, true, 16, 1024, 32 * 1024, 32, false, 0);

    public TargetCaps withArrayHeaderBytes(int bytes) {
        return new TargetCaps(dialect, computeCapability, fp64, fp16, bytes, maxWorkGroupSize, localMemoryBytes, warpSize, deviceLaunch, nvrtcVersion);
    }

    /** NVRTC 12.4, the first to emit PTX ISA 8.4 (FP8 {@code mma.sync}). */
    public static final int NVRTC_PTX_ISA_8_4 = 12004;

    /** Warp-level tensor-core MMA ({@code mma.sync} m16n8k16/k32) and {@code cp.async}: CUDA sm_80 or newer. */
    public boolean mma() {
        return dialect == Dialect.CUDA && computeCapability >= 80;
    }

    /**
     * FP8 (e4m3, e5m2) tensor-core MMA: sm_89 or newer and an NVRTC that emits PTX ISA 8.4. An unknown
     * NVRTC version (0, headless use) is not held against the device.
     */
    public boolean fp8Mma() {
        return mma() && computeCapability >= 89 && (nvrtcVersion <= 0 || nvrtcVersion >= NVRTC_PTX_ISA_8_4);
    }
}
