/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public abstract class OCLKernelScheduler {

    protected final OCLDeviceContext deviceContext;

    protected double min;
    protected double max;

    public static final String WARNING_FPGA_THREAD_LOCAL = "[TornadoVM OCL] Warning: TornadoVM changed the user-defined local size to: " + Arrays.toString(
            OCLFPGAScheduler.DEFAULT_LOCAL_WORK_SIZE) + ".";

    public static final String WARNING_THREAD_LOCAL = "[TornadoVM OCL] Warning: TornadoVM changed the user-defined local size to null. Now, the OpenCL driver will select the best configuration.";

    OCLKernelScheduler(final OCLDeviceContext context) {
        deviceContext = context;
    }

    public abstract void calculateGlobalWork(final TaskMetaData meta, long batchThreads);

    public abstract void calculateLocalWork(final TaskMetaData meta);

    public int submit(long executionPlanId, final OCLKernel kernel, final TaskMetaData meta, long batchThreads) {
        return submit(executionPlanId, kernel, meta, null, batchThreads);
    }

    private void updateProfiler(long executionPlanId, final int taskEvent, final TaskMetaData meta) {
        if (TornadoOptions.isProfilerEnabled()) {
            Event tornadoKernelEvent = deviceContext.resolveEvent(executionPlanId, taskEvent);
            tornadoKernelEvent.waitForEvents(executionPlanId);
            long timer = meta.getProfiler().getTimer(ProfilerType.TOTAL_KERNEL_TIME);
            // Register globalTime
            meta.getProfiler().setTimer(ProfilerType.TOTAL_KERNEL_TIME, timer + tornadoKernelEvent.getElapsedTime());
            // Register the time for the task
            meta.getProfiler().setTaskTimer(ProfilerType.TASK_KERNEL_TIME, meta.getId(), tornadoKernelEvent.getElapsedTime());
            // Register the dispatch time of the kernel
            long dispatchValue = meta.getProfiler().getTimer(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME);
            dispatchValue += tornadoKernelEvent.getDriverDispatchTime();
            meta.getProfiler().setTimer(ProfilerType.TOTAL_DISPATCH_KERNEL_TIME, dispatchValue);
            meta.getProfiler().setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, meta.getId(), deviceContext.getPowerUsage());
        }
    }

    public int launch(long executionPlanId, final OCLKernel kernel, final TaskMetaData meta, final int[] waitEvents, long batchThreads) {
        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            long[] global = grid.getGlobalWork();
            long[] offset = grid.getGlobalOffset();
            long[] local = grid.getLocalWork();
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, grid.dimension(), offset, global, local, waitEvents);
        } else {
            return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), (meta.shouldUseOpenCLDriverScheduling()
                    ? null
                    : meta.getLocalWork()), waitEvents);
        }
    }

    /**
     * Checks if the selected local work group fits on the target device. If it does
     * not fit, it sets the local work group to null, so the OpenCL driver chooses a
     * default value. In this case, the threads configured in the local work sizes
     * depends on each OpenCL driver.
     *
     * @param meta
     *     TaskMetaData.
     */
    private void checkLocalWorkGroupFitsOnDevice(final TaskMetaData meta) {
        WorkerGrid grid = meta.getWorkerGrid(meta.getId());
        long[] local = grid.getLocalWork();
        if (local != null) {
            OCLGridInfo gridInfo = new OCLGridInfo(deviceContext, local);
            boolean checkedDimensions = gridInfo.checkGridDimensions();
            if (!checkedDimensions) {
                if (deviceContext.isPlatformFPGA()) {
                    System.out.println(WARNING_FPGA_THREAD_LOCAL);
                    grid.setLocalWork(64, 1, 1);
                    grid.setNumberOfWorkgroupsToNull();
                } else {
                    System.out.println(WARNING_THREAD_LOCAL);
                    grid.setLocalWorkToNull();
                    grid.setNumberOfWorkgroupsToNull();
                }
            }
        }
    }

    public int submit(long executionPlanId, final OCLKernel kernel, final TaskMetaData meta, final int[] waitEvents, long batchThreads) {
        if (!meta.isWorkerGridAvailable()) {
            if (!meta.isGlobalWorkDefined()) {
                calculateGlobalWork(meta, batchThreads);
            }
            if (!meta.isLocalWorkDefined()) {
                calculateLocalWork(meta);
            }
        } else {
            checkLocalWorkGroupFitsOnDevice(meta);
        }

        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }
        final int taskEvent = launch(executionPlanId, kernel, meta, waitEvents, batchThreads);
        updateProfiler(executionPlanId, taskEvent, meta);
        return taskEvent;
    }

}
