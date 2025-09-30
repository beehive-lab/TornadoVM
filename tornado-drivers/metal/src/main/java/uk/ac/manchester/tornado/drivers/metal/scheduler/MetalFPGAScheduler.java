/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.metal.scheduler;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalKernel;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalFPGAScheduler extends MetalKernelScheduler {

    public static final long[] DEFAULT_LOCAL_WORK_SIZE = { 64, 1, 1 };
    public static final String WARNING_FPGA_DEFAULT_LOCAL = "[TornadoVM Metal] Warning: TornadoVM uses as default local work group size for FPGAs: " + Arrays.toString(DEFAULT_LOCAL_WORK_SIZE) + ".";
    private static final int WARP = 32;

    public MetalFPGAScheduler(final MetalDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            if (value % WARP != 0) {
                value = ((value / WARP) + 1) * WARP;
            }
            globalWork[i] = value;
        }
    }

    @Override
    public void calculateLocalWork(final TaskDataContext meta) {
        final long[] localWork = DEFAULT_LOCAL_WORK_SIZE;
        switch (meta.getDims()) {
            case 3:
                setLocalWork(3, localWork, meta);
                break;
            case 2:
                setLocalWork(2, localWork, meta);
                break;
            case 1:
                setLocalWork(1, localWork, meta);
                break;
            default:
                break;
        }
    }

    @Override
    public void checkAndAdaptLocalWork(TaskDataContext meta) {
    }

    @Override
    public int launch(long executionPlanId, final MetalKernel kernel, final TaskDataContext meta, final long[] waitEvents, long batchThreads) {
        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            long[] global = grid.getGlobalWork();
            long[] offset = grid.getGlobalOffset();
            long[] local = grid.getLocalWork();
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, grid.dimension(), offset, global, local, waitEvents);
        } else {
            if (meta.shouldUseMetalDriverScheduling()) {
                System.out.println(WARNING_FPGA_DEFAULT_LOCAL);
            }
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), meta.getLocalWork(), waitEvents);
        }
    }

    @Override
    public long[] getDefaultLocalWorkGroup() {
        return DEFAULT_LOCAL_WORK_SIZE;
    }

    private void setLocalWork(final int dimensions, long[] localWork, final TaskDataContext meta) {
        for (int i = 0; i < dimensions; i++) {
            localWork[i] = calculateGroupSize(DEFAULT_LOCAL_WORK_SIZE[i], meta.getGlobalWork()[i]);
        }
        meta.setLocalWork(localWork);
    }

    private int calculateGroupSize(long maxWorkItemSizes, long globalWorkSize) {
        int value = (int) Math.min(maxWorkItemSizes, globalWorkSize);
        while (globalWorkSize % value != 0) {
            value--;
        }
        if (value < maxWorkItemSizes) {
            throw new RuntimeException("[ERROR] Minimum input of 64 elements to run on the FPGA");
        }
        return value;
    }
}
