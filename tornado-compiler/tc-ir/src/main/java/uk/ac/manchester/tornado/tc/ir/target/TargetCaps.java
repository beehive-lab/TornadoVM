/*
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
 * @param arch                the AMD GPU architecture a HIP kernel is compiled for ({@code gfx942}), without
 *                            feature suffixes; empty elsewhere
 */
public record TargetCaps(Dialect dialect, int computeCapability, boolean fp64, boolean fp16, int arrayHeaderBytes, int maxWorkGroupSize, int localMemoryBytes, int warpSize,
        boolean deviceLaunch, int nvrtcVersion, String arch) {

    public enum Dialect {
        CUDA, OPENCL, METAL, HIP
    }

    public TargetCaps {
        arch = arch == null ? "" : arch;
    }

    public TargetCaps(Dialect dialect, int computeCapability, boolean fp64, boolean fp16, int arrayHeaderBytes, int maxWorkGroupSize, int localMemoryBytes, int warpSize, boolean deviceLaunch,
            int nvrtcVersion) {
        this(dialect, computeCapability, fp64, fp16, arrayHeaderBytes, maxWorkGroupSize, localMemoryBytes, warpSize, deviceLaunch, nvrtcVersion, "");
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

    /**
     * An AMD GPU through HIP and hipRTC, for headless use: an MI300-class (gfx942) device with 64-wide
     * wavefronts, 64 KB of LDS per work-group and no device-side launches.
     */
    public static final TargetCaps HIP_GFX942 = hip("gfx942", true, true, 16, 1024, 64 * 1024, 64);

    /**
     * A HIP device as its driver describes it. HIP has no compute capability in the CUDA sense (it
     * reports the gfx major and minor, which must not be read as an sm_ number), and kernels cannot
     * launch kernels.
     */
    public static TargetCaps hip(String arch, boolean fp64, boolean fp16, int arrayHeaderBytes, int maxWorkGroupSize, int localMemoryBytes, int warpSize) {
        return new TargetCaps(Dialect.HIP, 0, fp64, fp16, arrayHeaderBytes, maxWorkGroupSize, localMemoryBytes, warpSize, false, 0, arch);
    }

    public TargetCaps withArrayHeaderBytes(int bytes) {
        return new TargetCaps(dialect, computeCapability, fp64, fp16, bytes, maxWorkGroupSize, localMemoryBytes, warpSize, deviceLaunch, nvrtcVersion, arch);
    }

    /**
     * Whether KernelContext's warp operations, which are defined for 32-lane warps, run on this target:
     * 32-lane warps, or HIP on a 64-wide wavefront, which treats each half of the wavefront as a warp.
     */
    public boolean warps32() {
        return warpSize == 32 || dialect == Dialect.HIP && warpSize == 64;
    }

    /** NVRTC 12.4, the first to emit PTX ISA 8.4 (FP8 {@code mma.sync}). */
    public static final int NVRTC_PTX_ISA_8_4 = 12004;

    /**
     * Warp-level tensor-core MMA ({@code mma.sync} m16n8k16/k32) and {@code cp.async}: CUDA sm_80 or newer,
     * or an AMD GPU with matrix cores (MFMA on CDNA, WMMA on RDNA3, RDNA4 and gfx1250).
     */
    public boolean mma() {
        return switch (dialect) {
            case CUDA -> computeCapability >= 80;
            case HIP -> matrixCores() != MatrixCores.NONE;
            default -> false;
        };
    }

    /** The matrix instructions a HIP kernel lowers KernelContext's MMA operations to. */
    public enum MatrixCores {
        /** None this lowering targets. */
        NONE,
        /** CDNA (gfx908, gfx90a, gfx940, gfx941, gfx942, gfx950): MFMA. */
        MFMA,
        /** RDNA3 (gfx11): WMMA. */
        WMMA_GFX11,
        /** RDNA4 (gfx1200, gfx1201): WMMA. */
        WMMA_GFX12,
        /** gfx1250: its own WMMA forms (16x16x32 for 16-bit, 16x16x64 for 8-bit inputs). */
        WMMA_GFX1250
    }

    /**
     * The matrix instructions of {@link #arch}; {@link MatrixCores#NONE} for a GPU without them, and for any
     * other dialect. The architectures are those the MMA helpers' preprocessor routing names (Helpers).
     */
    public MatrixCores matrixCores() {
        if (dialect != Dialect.HIP) {
            return MatrixCores.NONE;
        }
        if (arch.equals("gfx908") || arch.equals("gfx90a") || arch.equals("gfx940") || arch.equals("gfx941") || arch.equals("gfx942") || arch.equals("gfx950")) {
            return MatrixCores.MFMA;
        }
        if (arch.startsWith("gfx11")) {
            return MatrixCores.WMMA_GFX11;
        }
        if (arch.equals("gfx1200") || arch.equals("gfx1201")) {
            return MatrixCores.WMMA_GFX12;
        }
        if (arch.equals("gfx1250")) {
            return MatrixCores.WMMA_GFX1250;
        }
        return MatrixCores.NONE;
    }

    /**
     * FP8 (e4m3, e5m2) tensor-core MMA: sm_89 or newer and an NVRTC that emits PTX ISA 8.4. An unknown
     * NVRTC version (0, headless use) is not held against the device.
     */
    public boolean fp8Mma() {
        if (dialect == Dialect.HIP) {
            // FP8 inputs are multiplied natively on gfx1250 and elsewhere widened exactly to halves for the
            // half-precision instructions.
            return mma();
        }
        return mma() && computeCapability >= 89 && (nvrtcVersion <= 0 || nvrtcVersion >= NVRTC_PTX_ISA_8_4);
    }
}
