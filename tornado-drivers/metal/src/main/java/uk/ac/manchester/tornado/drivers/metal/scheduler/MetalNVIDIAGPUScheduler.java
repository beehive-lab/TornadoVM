/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

import java.util.Arrays;

public class MetalNVIDIAGPUScheduler extends MetalKernelScheduler {

    private static final int WARP_SIZE = 32;
    private boolean ADJUST_IRREGULAR = false;

    private final long[] maxWorkItemSizes;

    public MetalNVIDIAGPUScheduler(final MetalDeviceContext context) {
        super(context);
        MetalTargetDevice device = context.getDevice();

        maxWorkItemSizes = device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            if (ADJUST_IRREGULAR && (value % WARP_SIZE != 0)) {
                value = ((value / WARP_SIZE) + 1) * WARP_SIZE;
            }
            globalWork[i] = value;
        }
    }

    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        final long[] localWork = meta.initLocalWork();
        switch (meta.getDims()) {
            case 3:
                localWork[2] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[2], meta.getGlobalWork()[2], 3);
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1], 3);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0], 3);
                break;
            case 2:
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1], 2);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0], 2);
                break;
            case 1:
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0], 1);
                break;
            default:
                break;
        }
    }

    /**
     * Checks if the selected local work group does not exceed the maximum work group size permitted by the driver.
     * If it does, it uses a heuristic to set the local work group.
     *
     * @param meta
     *     TaskMetaData.
     */
    @Override
    public void checkAndAdaptLocalWork(final TaskDataContext meta) {
        final long[] localWork = meta.getLocalWork();
        if (localWork == null) {
            return;
        }
        switch (meta.getDims()) {
            case 3:
                localWork[2] = checkAndAdaptLocalDimensions(localWork)[2];
                localWork[1] = checkAndAdaptLocalDimensions(localWork)[1];
                localWork[0] = checkAndAdaptLocalDimensions(localWork)[0];
                break;
            case 2:
                localWork[1] = checkAndAdaptLocalDimensions(localWork)[1];
                localWork[0] = checkAndAdaptLocalDimensions(localWork)[0];
                break;
            case 1:
                localWork[0] = checkAndAdaptLocalDimensions(localWork)[0];
                break;
            default:
                break;
        }
    }

    private long[] checkAndAdaptLocalDimensions(long[] localWorkGroups) {
        long[] blockMaxWorkGroupSize = deviceContext.getDevice().getDeviceMaxWorkGroupSize();
        long maxWorkGroupSize = Arrays.stream(blockMaxWorkGroupSize).sum();
        long totalThreads = Arrays.stream(localWorkGroups).reduce(1, (a, b) -> a * b);

        if (totalThreads > maxWorkGroupSize) {
            //Get the remaining valid number of local work-items
            return adaptLocalDimensions(localWorkGroups, maxWorkGroupSize);
        }

        return localWorkGroups;
    }

    private long[] adaptLocalDimensions(long[] localWorkGroups, long maxWorkGroupSize) {
        long[] newLocalWorkGroup = new long[localWorkGroups.length];
        switch (localWorkGroups.length) {
            case 3:
                newLocalWorkGroup[0] = localWorkGroups[0];
                newLocalWorkGroup[1] = localWorkGroups[1];
                newLocalWorkGroup[2] = maxWorkGroupSize / (newLocalWorkGroup[0] * newLocalWorkGroup[1]);
                break;
            case 2:
                newLocalWorkGroup[1] = maxWorkGroupSize / localWorkGroups[0];
                break;
            case 1:
                newLocalWorkGroup[0] = maxWorkGroupSize;
                break;
            default:
                break;
        }
        return newLocalWorkGroup;
    }

    private int calculateGroupSize(long maxBlockSize, long globalWorkSize, int dim) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(maxBlockSize, globalWorkSize);
        if (value == 0) {
            return 1;
        }
        while (globalWorkSize % value != 0) {
            value--;
        }
        if (value >= 32 && dim > 1) {
            value /= 2;
        }
        return value;
    }

    private long[] calculateEffectiveMaxWorkItemSizes(TaskDataContext metaData) {
        long[] localWorkGroups = new long[] { 1, 1, 1 };
        if (metaData.getDims() == 1) {
            localWorkGroups[0] = maxWorkItemSizes[0];
        } else {
            for (int i = 0; i < metaData.getDims(); i++) {
                localWorkGroups[i] = (long) Math.sqrt(maxWorkItemSizes[i]);
            }
        }
        return localWorkGroups;
    }
}
