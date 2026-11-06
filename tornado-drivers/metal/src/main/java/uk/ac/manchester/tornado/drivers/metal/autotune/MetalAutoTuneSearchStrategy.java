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
package uk.ac.manchester.tornado.drivers.metal.autotune;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.enums.AutoTuneStrategyType;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.drivers.metal.runtime.MetalTornadoDevice;
import uk.ac.manchester.tornado.runtime.autotune.spi.AutoTuneSearchStrategy;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * Metal implementation of {@link AutoTuneSearchStrategy}. Only {@link AutoTuneStrategyType#EXHAUSTIVE}
 * is implemented; every {@code generateCandidates*} method throws {@link UnsupportedOperationException}
 * for {@link AutoTuneStrategyType#SMART}.
 *
 * <p>
 * With {@code EXHAUSTIVE}, the returned list holds {@code null} (backend default), then the seed if there
 * is one, then every size within the device limits, read from the underlying {@link MetalTargetDevice}.
 * For 1D that is every size from 1 to {@code getDeviceMaxWorkItemSizes()[0]}. For 2D and 3D it is every
 * ordered combination within the per-dimension maxima whose product is at most the threadgroup maximum,
 * the sum of {@code getDeviceMaxWorkGroupSize()}, so {@code (4, 8)} and {@code (8, 4)} are separate
 * candidates. Sizes are not filtered by divisibility of the global work size or by SIMD-group alignment,
 * and the seed may also appear again among the enumerated sizes.
 * </p>
 */
public final class MetalAutoTuneSearchStrategy implements AutoTuneSearchStrategy {

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof MetalTornadoDevice;
    }

    @Override
    public List<int[]> generateCandidates1D(long globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType) {
        return switch (strategyType) {
            case EXHAUSTIVE -> exhaustive1D(device, userSuppliedLocalWork);
            case SMART -> throw new UnsupportedOperationException("[ERROR] SMART auto-tune strategy is not yet implemented for Metal.");
        };
    }

    @Override
    public List<int[]> generateCandidates2D(long[] globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType) {
        return switch (strategyType) {
            case EXHAUSTIVE -> exhaustive2D(device, userSuppliedLocalWork);
            case SMART -> throw new UnsupportedOperationException("[ERROR] SMART auto-tune strategy is not yet implemented for Metal.");
        };
    }

    @Override
    public List<int[]> generateCandidates3D(long[] globalWorkSize, TornadoXPUDevice device, int[] userSuppliedLocalWork, AutoTuneStrategyType strategyType) {
        return switch (strategyType) {
            case EXHAUSTIVE -> exhaustive3D(device, userSuppliedLocalWork);
            case SMART -> throw new UnsupportedOperationException("[ERROR] SMART auto-tune strategy is not yet implemented for Metal.");
        };
    }

    
    private MetalTargetDevice physicalDevice(TornadoXPUDevice device) {
        return ((MetalTornadoDevice) device).getPhysicalDevice();
    }

    
    private long threadgroupMax(TornadoXPUDevice device) {
        long[] maxGroupSize = physicalDevice(device).getDeviceMaxWorkGroupSize();
        return Arrays.stream(maxGroupSize).sum();
    }

    private List<int[]> exhaustive1D(TornadoXPUDevice device, int[] userSuppliedLocalWork) {
        long[] maxWorkItemSizes = physicalDevice(device).getDeviceMaxWorkItemSizes();
        long max = maxWorkItemSizes[0];

        List<int[]> candidates = new ArrayList<>();
        candidates.add(null);
        if (userSuppliedLocalWork != null) {
            candidates.add(userSuppliedLocalWork);
        }
        for (long a = 1; a <= max; a++) {
            candidates.add(new int[] { (int) a });
        }
        return candidates;
    }

    private List<int[]> exhaustive2D(TornadoXPUDevice device, int[] userSuppliedLocalWork) {
        long[] maxWorkItemSizes = physicalDevice(device).getDeviceMaxWorkItemSizes();
        long maxA = maxWorkItemSizes[0];
        long maxB = maxWorkItemSizes[1];
        long threadgroupMax = threadgroupMax(device);

        List<int[]> candidates = new ArrayList<>();
        candidates.add(null);
        if (userSuppliedLocalWork != null) {
            candidates.add(userSuppliedLocalWork);
        }
        for (long a = 1; a <= maxA; a++) {
            for (long b = 1; b <= maxB; b++) {
                if (a * b <= threadgroupMax) {
                    candidates.add(new int[] { (int) a, (int) b });
                }
            }
        }
        return candidates;
    }

    private List<int[]> exhaustive3D(TornadoXPUDevice device, int[] userSuppliedLocalWork) {
        long[] maxWorkItemSizes = physicalDevice(device).getDeviceMaxWorkItemSizes();
        long maxA = maxWorkItemSizes[0];
        long maxB = maxWorkItemSizes[1];
        long maxC = maxWorkItemSizes[2];
        long threadgroupMax = threadgroupMax(device);

        List<int[]> candidates = new ArrayList<>();
        candidates.add(null);
        if (userSuppliedLocalWork != null) {
            candidates.add(userSuppliedLocalWork);
        }
        for (long a = 1; a <= maxA; a++) {
            for (long b = 1; b <= maxB; b++) {
                for (long c = 1; c <= maxC; c++) {
                    if (a * b * c <= threadgroupMax) {
                        candidates.add(new int[] { (int) a, (int) b, (int) c });
                    }
                }
            }
        }
        return candidates;
    }
}