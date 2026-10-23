/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.scheduler;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDAKernel;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class CUDAAMDScheduler extends CUDAKernelScheduler {

    private static final int WARP_SIZE = 64;
    private boolean ADJUST_IRREGULAR = false;

    private final long[] maxWorkItemSizes;

    public CUDAAMDScheduler(final CUDADeviceContext context) {
        super(context);
        CUDATargetDevice device = context.getDevice();
        maxWorkItemSizes = device.getDeviceMaxWorkItemSizes();
    }

    @Override
    public int launch(long executionPlanId, CUDAKernel kernel, TaskDataContext meta, int[] waitEvents, long batchThreads) {
        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            long[] global = grid.getGlobalWork();
            long[] offset = grid.getGlobalOffset();
            long[] local = grid.getLocalWork();
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, grid.dimension(), offset, global, local, waitEvents);
        } else {
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), null, waitEvents);
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
        final long[] localWork = meta.initLocalWork();
        switch (meta.getDims()) {
            case 3:
                localWork[2] = calculateGroupSize(maxWorkItemSizes[2], meta.getOpenCLGpuBlock2DY(), meta.getGlobalWork()[2]);
            case 2:
                localWork[1] = calculateGroupSize(maxWorkItemSizes[1], meta.getOpenCLGpuBlock2DY(), meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(maxWorkItemSizes[0], meta.getOpenCLGpuBlock2DX(), meta.getGlobalWork()[0]);
                break;
            case 1:
                localWork[0] = calculateGroupSize(maxWorkItemSizes[0], meta.getOpenCLGpuBlockX(), meta.getGlobalWork()[0]);
                break;
            default:
                break;
        }
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
