/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalKernel;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class MetalAMDScheduler extends MetalKernelScheduler {

    private static final int WARP_SIZE = 64;
    private boolean ADJUST_IRREGULAR = false;

    private final long[] maxWorkItemSizes;

    public MetalAMDScheduler(final MetalDeviceContext context) {
        super(context);
        MetalTargetDevice device = context.getDevice();
        maxWorkItemSizes = device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public int launch(long executionPlanId, MetalKernel kernel, TaskDataContext meta, long[] waitEvents, long batchThreads) {
        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            long[] global = grid.getGlobalWork();
            long[] offset = grid.getGlobalOffset();
            long[] local = grid.getLocalWork();
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, grid.dimension(), offset, global, local, waitEvents);
        } else {
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), (long[]) null, waitEvents);
        }
    }

    @Override
    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            // adjust for irregular problem sizes
            if (ADJUST_IRREGULAR && (value % WARP_SIZE != 0)) {
                value = ((value / WARP_SIZE) + 1) * WARP_SIZE;
            }
            globalWork[i] = value;
        }
    }

    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        throw TornadoInternalError.unimplementedMetal();
//        final long[] localWork = meta.initLocalWork();
//        switch (meta.getDims()) {
//            case 3:
//                localWork[2] = calculateGroupSize(maxWorkItemSizes[2], meta.getMetalGpuBlock2DY(), meta.getGlobalWork()[2]);
//            case 2:
//                localWork[1] = calculateGroupSize(maxWorkItemSizes[1], meta.getMetalGpuBlock2DY(), meta.getGlobalWork()[1]);
//                localWork[0] = calculateGroupSize(maxWorkItemSizes[0], meta.getMetalGpuBlock2DX(), meta.getGlobalWork()[0]);
//                break;
//            case 1:
//                localWork[0] = calculateGroupSize(maxWorkItemSizes[0], meta.getMetalGpuBlockX(), meta.getGlobalWork()[0]);
//                break;
//            default:
//                break;
//        }
    }

    @Override
    public void checkAndAdaptLocalWork(TaskDataContext meta) {
    }

    private int calculateGroupSize(long maxBlockSize, long customBlockSize, long globalWorkSize) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }
        int value = (int) Math.min(Math.max(maxBlockSize, customBlockSize), globalWorkSize);
        if (value == 0) {
            return 1;
        }
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }
}
