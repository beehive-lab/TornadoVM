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
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import java.util.Arrays;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Thread scheduler for Apple Silicon GPUs.
 *
 * <p>Apple Silicon GPUs have a SIMD group size of 32 (the Metal equivalent of a warp/wavefront).
 * This scheduler aligns global work to multiples of {@code SIMD_GROUP_SIZE} and selects local
 * (threadgroup) sizes that are multiples of {@code SIMD_GROUP_SIZE}, ensuring full SIMD lane
 * utilisation and avoiding partial wavefronts.
 */
public class MetalAppleGPUScheduler extends MetalKernelScheduler {

    /**
     * SIMD group size for Apple Silicon (M-series and A-series) GPUs.
     * All threadgroup sizes should be multiples of this value.
     */
    public static final int SIMD_GROUP_SIZE = 32;

    private final long[] maxWorkItemSizes;

    public MetalAppleGPUScheduler(final MetalDeviceContext context) {
        super(context);
        MetalTargetDevice device = context.getDevice();
        maxWorkItemSizes = device.getDeviceMaxWorkItemSizes();
    }

    /**
     * Sets global work to the domain cardinality (or batch size), unchanged.
     *
     * <p>Global work is NOT rounded up to SIMD boundaries here because TornadoVM's
     * {@code @Reduce} infrastructure pre-sizes the reduction output array based on the
     * domain cardinality. Rounding up would change the workgroup count and corrupt those
     * arrays. SIMD alignment is achieved instead by {@link #calculateLocalWork}, which
     * selects a threadgroup size that is a multiple of {@code SIMD_GROUP_SIZE}.
     */
    @Override
    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            globalWork[i] = value;
        }
    }

    /**
     * Selects a threadgroup (local) size that is the largest multiple of {@code SIMD_GROUP_SIZE}
     * which evenly divides the global work size, capped by the device maximum work-item size.
     *
     * <p>For 2D and 3D dispatches the effective per-dimension cap is {@code sqrt(maxWorkItemSize)}
     * to leave room across dimensions. The z-dimension of a 3D dispatch is fixed at 1.
     */
    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        final long[] localWork = meta.initLocalWork();
        switch (meta.getDims()) {
            case 3:
                localWork[2] = 1;
                localWork[1] = calculateGroupSize(effectiveMaxPerDim(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(effectiveMaxPerDim(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 2:
                localWork[1] = calculateGroupSize(effectiveMaxPerDim(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(effectiveMaxPerDim(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 1:
                localWork[0] = calculateGroupSize(maxWorkItemSizes[0], meta.getGlobalWork()[0]);
                break;
            default:
                break;
        }
    }

    /**
     * Validates that the total number of threads in the threadgroup does not exceed the device
     * maximum work-group size. If it does, the first dimension is clamped to the maximum.
     */
    @Override
    public void checkAndAdaptLocalWork(final TaskDataContext meta) {
        long[] localWork = meta.getLocalWork();
        if (localWork == null) {
            return;
        }
        long[] maxGroupSize = deviceContext.getDevice().getDeviceMaxWorkGroupSize();
        long maxTotal = Arrays.stream(maxGroupSize).sum();
        long total = Arrays.stream(localWork).reduce(1, (a, b) -> a * b);
        if (total > maxTotal) {
            localWork[0] = maxTotal;
        }
    }

    /**
     * Returns the largest multiple of {@code SIMD_GROUP_SIZE} that is ≤ {@code maxBlockSize} and
     * evenly divides {@code globalWorkSize}. Falls back to the largest plain divisor if no
     * SIMD-aligned candidate exists.
     */
    private int calculateGroupSize(long maxBlockSize, long globalWorkSize) {
        long cap = Math.min(maxBlockSize, globalWorkSize);

        // Prefer largest multiple of SIMD_GROUP_SIZE that divides globalWorkSize
        long candidate = (cap / SIMD_GROUP_SIZE) * SIMD_GROUP_SIZE;
        while (candidate >= SIMD_GROUP_SIZE) {
            if (globalWorkSize % candidate == 0) {
                return (int) candidate;
            }
            candidate -= SIMD_GROUP_SIZE;
        }

        // Fallback: largest plain divisor ≤ cap
        int value = (int) cap;
        while (value > 1 && globalWorkSize % value != 0) {
            value--;
        }
        return Math.max(1, value);
    }

    /**
     * Per-dimension cap for 2D/3D dispatches: {@code sqrt(maxWorkItemSize[i])} so that the
     * product across dimensions stays within device limits.
     */
    private long[] effectiveMaxPerDim(TaskDataContext meta) {
        long[] caps = new long[] { 1, 1, 1 };
        for (int i = 0; i < meta.getDims(); i++) {
            caps[i] = (long) Math.sqrt(maxWorkItemSizes[i]);
        }
        return caps;
    }
}
