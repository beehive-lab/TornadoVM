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
package uk.ac.manchester.tornado.drivers.cuda.scheduler;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.runtime.common.UpsMeterReader;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDAGridInfo;
import uk.ac.manchester.tornado.drivers.cuda.CUDAKernel;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public abstract class CUDAKernelScheduler {

    protected final CUDADeviceContext deviceContext;

    protected double min;
    protected double max;

    public final String WARNING_FPGA_THREAD_LOCAL = "[TornadoVM CUDA] Warning: TornadoVM changed the user-defined local size to: " + ((getDefaultLocalWorkGroup() != null)
            ? Arrays.toString(getDefaultLocalWorkGroup())
            : "null") + ".";

    public static final String WARNING_THREAD_LOCAL = "[TornadoVM CUDA] Warning: TornadoVM changed the user-defined local size to null. Now, the CUDADriver driver will select the best configuration.";

    protected CUDAKernelScheduler(final CUDADeviceContext context) {
        deviceContext = context;
    }

    public abstract void calculateGlobalWork(final TaskDataContext meta, long batchThreads);

    public abstract void calculateLocalWork(final TaskDataContext meta);

    public abstract void checkAndAdaptLocalWork(final TaskDataContext meta);

    public long[] getDefaultLocalWorkGroup() {
        return null;
    }

    public int submit(long executionPlanId, final CUDAKernel kernel, final TaskDataContext meta, long batchThreads) {
        return submit(executionPlanId, kernel, meta, null, batchThreads);
    }

    private void updateProfiler(long executionPlanId, final int taskEvent, final TaskDataContext meta) {
        // Skipped while capturing into a CUDA graph: waiting on (or querying) an event that was
        // recorded into a capturing stream is illegal and invalidates the capture.
        if (TornadoOptions.isProfilerEnabled() && !deviceContext.isStreamCapturing(executionPlanId)) {
            // Metrics captured before blocking
            meta.getProfiler().setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, meta.getId(), deviceContext.getPowerUsage());
            if (TornadoOptions.isUpsReaderEnabled()) {
                meta.getProfiler().setSystemPowerConsumption(ProfilerType.SYSTEM_POWER_CONSUMPTION_W, meta.getId(), (UpsMeterReader.getOutputPowerMetric() != null)
                        ? Long.parseLong(UpsMeterReader.getOutputPowerMetric())
                        : -1);
                meta.getProfiler().setSystemVoltage(ProfilerType.SYSTEM_VOLTAGE_V, meta.getId(), (UpsMeterReader.getOutputVoltageMetric() != null)
                        ? Long.parseLong(UpsMeterReader.getOutputVoltageMetric())
                        : -1);
            }

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
        }
    }

    public int launch(long executionPlanId, final CUDAKernel kernel, final TaskDataContext meta, final int[] waitEvents, long batchThreads) {
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
     * not fit, it sets the local work group to null, so the CUDADriver driver chooses a
     * default value. In this case, the threads configured in the local work sizes
     * depends on each CUDADriver driver.
     *
     * @param meta
     *     TaskMetaData.
     */
    private void checkLocalWorkGroupFitsOnDevice(final TaskDataContext meta) {
        WorkerGrid grid = meta.getWorkerGrid(meta.getId());
        long[] local = grid.getLocalWork();
        if (local != null) {
            CUDAGridInfo gridInfo = new CUDAGridInfo(deviceContext, local);
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

    public int submit(long executionPlanId, final CUDAKernel kernel, final TaskDataContext meta, final int[] waitEvents, long batchThreads) {
        if (!meta.isWorkerGridAvailable()) {
            if (!meta.isGlobalWorkDefined()) {
                calculateGlobalWork(meta, batchThreads);
            }
            if (!meta.isLocalWorkDefined()) {
                // For multi-dimensional kernels, size the block from the CUDA occupancy API,
                // which accounts for the kernel's register/shared-memory usage. This mirrors the
                // PTX backend and prevents register-heavy 2D/3D kernels from requesting an
                // unlaunchable block (CUDA_ERROR_LAUNCH_OUT_OF_RESOURCES). 1D kernels keep the
                // existing heuristic so reduction thread/partial layout is unaffected.
                int maxBlockThreads = (meta.getDims() > 1) ? kernel.getMaxPotentialBlockSize() : 0;
                if (maxBlockThreads > 0) {
                    calculateLocalWorkFromMaxBlock(meta, maxBlockThreads);
                } else {
                    calculateLocalWork(meta);
                    checkAndAdaptLocalWork(meta);
                }
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

    /**
     * Distributes an occupancy-suggested maximum block size ({@code maxBlockThreads} total
     * threads) across the kernel's dimensions, choosing per-dimension sizes that divide the
     * global work evenly. Mirrors the PTX backend's occupancy-based block sizing.
     */
    protected void calculateLocalWorkFromMaxBlock(final TaskDataContext meta, int maxBlockThreads) {
        final long[] localWork = meta.initLocalWork();
        final long[] globalWork = meta.getGlobalWork();
        final int dims = meta.getDims();
        final long perDimensionMax = (long) Math.pow(maxBlockThreads, 1.0 / dims);
        for (int i = 0; i < dims; i++) {
            localWork[i] = blockSizeForDimension(perDimensionMax, globalWork[i]);
        }
    }

    private static long blockSizeForDimension(long maxBlockSize, long globalWorkSize) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }
        long value = Math.min(maxBlockSize, globalWorkSize);
        if (value <= 0) {
            return 1;
        }
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }

}
