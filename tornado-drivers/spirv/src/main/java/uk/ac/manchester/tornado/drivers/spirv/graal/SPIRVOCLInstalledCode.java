/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLErrorCode;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVKernelStackFrame;
import uk.ac.manchester.tornado.drivers.spirv.ocl.SPIRVOCLNativeDispatcher;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVOCLInstalledCode extends SPIRVInstalledCode {

    private boolean valid;
    public static final String WARNING_THREAD_LOCAL = "[TornadoVM SPIR-V] Warning: TornadoVM changed the user-defined local thread sizes to the suggested values by the driver.";
    private static final int WARP_SIZE = 32;
    private boolean ADJUST_IRREGULAR = false;

    public SPIRVOCLInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name, spirvModule, deviceContext);
        this.valid = true;
    }

    @Override
    public int launchWithDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public int launchWithoutDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskMetaData meta, long batchThreads) {

        // Set kernel args
        setKernelArgs(executionPlanId, (SPIRVKernelStackFrame) callWrapper);
        SPIRVOCLModule module = (SPIRVOCLModule) spirvModule;
        long kernelPointer = module.getKernelPointer();

        // Calculate the GWS and LWS
        calculateGlobalAndLocalBlockOfThreads(meta, batchThreads);

        return submit(executionPlanId, kernelPointer, meta, null);
    }

    private void checkStatus(int status, String lowLevelFunction) {
        if (status != OCLErrorCode.CL_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + lowLevelFunction);
        }
    }

    private void setKernelArgs(long executionPlanId, final SPIRVKernelStackFrame callWrapper) {
        // Enqueue write
        callWrapper.enqueueWrite(executionPlanId, null);

        SPIRVOCLModule module = (SPIRVOCLModule) spirvModule;
        long kernelPointer = module.getKernelPointer();

        // device's kernel context
        SPIRVOCLNativeDispatcher dispatcher = new SPIRVOCLNativeDispatcher();
        int status = dispatcher.clSetKernelArg(kernelPointer, 0, Sizeof.LONG.getNumBytes(), callWrapper.toBuffer());
        checkStatus(status, "clSetKernelArg");

        // Set all user parameters to the SPIR-V kernel
        for (int argIndex = 0; argIndex < callWrapper.getCallArguments().size(); argIndex++) {
            int kernelParamIndex = argIndex + 1;
            KernelStackFrame.CallArgument arg = callWrapper.getCallArguments().get(argIndex);

            if (arg.getValue() instanceof KernelStackFrame.KernelContextArgument) {
                status = dispatcher.clSetKernelArg(kernelPointer, kernelParamIndex, Sizeof.LONG.getNumBytes(), callWrapper.toBuffer());
                checkStatus(status, "clSetKernelArg");
                continue;
            }

            if (RuntimeUtilities.isBoxedPrimitive(arg.getValue()) || arg.getValue().getClass().isPrimitive()) {
                if (!arg.isReferenceType()) {
                    continue;
                }
                status = dispatcher.clSetKernelArg(kernelPointer, kernelParamIndex, Sizeof.LONG.getNumBytes(), ((Number) arg.getValue()).longValue());
                checkStatus(status, "clSetKernelArg");
            } else {
                TornadoInternalError.shouldNotReachHere();
            }
        }
    }

    public int submit(long executionPlanId, long kernelPointer, final TaskMetaData meta, long[] waitEvents) {
        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }
        long[] kernelEvent = new long[1];
        final int taskEvent = launch(executionPlanId, kernelPointer, meta, waitEvents, kernelEvent);
        updateProfiler(executionPlanId, taskEvent, meta);
        return taskEvent;
    }

    public int launch(long executionPlanId, long kernelPointer, final TaskMetaData meta, long[] waitEvents, long[] kernelEvent) {
        SPIRVOCLNativeDispatcher dispatcher = new SPIRVOCLNativeDispatcher();
        OCLCommandQueue commandQueue = (OCLCommandQueue) deviceContext.getSpirvContext().getCommandQueueForDevice(executionPlanId, deviceContext.getDeviceIndex());
        long queuePointer = commandQueue.getCommandQueuePtr();

        if (meta.isWorkerGridAvailable()) {
            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            return dispatcher.clEnqueueNDRangeKernel(queuePointer, kernelPointer, //
                    meta.getDims(), grid.getGlobalOffset(), grid.getGlobalWork(), grid.getLocalWork(),//
                    waitEvents, kernelEvent);//
        } else {
            long[] localWorkGroup = null;
            if (!meta.shouldUseOpenCLDriverScheduling()) {
                localWorkGroup = meta.getLocalWork();
            }
            return dispatcher.clEnqueueNDRangeKernel(queuePointer, kernelPointer, //
                    meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), //
                    localWorkGroup, waitEvents, kernelEvent);//
        }
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
            // TODO: Add Power User Metric
            meta.getProfiler().setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, meta.getId(), deviceContext.getPowerUsage());
        }
    }

    private void calculateGlobalAndLocalBlockOfThreads(TaskMetaData meta, long batchThreads) {
        long[] globalWorkGroup = new long[3];
        long[] localWorkGroup = new long[3];
        Arrays.fill(globalWorkGroup, 1);
        Arrays.fill(localWorkGroup, 1);

        if (!meta.isGridSchedulerEnabled()) {
            int dims = meta.getDims();
            if (!meta.isGlobalWorkDefined()) {
                calculateGlobalWork(meta, batchThreads);
            }
            if (!meta.isLocalWorkDefined()) {
                calculateLocalWork(meta);
            }

            System.arraycopy(meta.getGlobalWork(), 0, globalWorkGroup, 0, dims);
            System.arraycopy(meta.getLocalWork(), 0, localWorkGroup, 0, dims);
        } else {
            checkLocalWorkGroupFitsOnDevice(meta);
            WorkerGrid worker = meta.getWorkerGrid(meta.getId());
            int dims = worker.dimension();
            System.arraycopy(worker.getGlobalWork(), 0, globalWorkGroup, 0, dims);
            if (worker.getLocalWork() != null) {
                System.arraycopy(worker.getLocalWork(), 0, localWorkGroup, 0, dims);
            }
        }
    }

    public void calculateLocalWork(final TaskMetaData meta) {
        final long[] localWork = meta.initLocalWork();

        switch (meta.getDims()) {
            case 3:
                localWork[2] = 1;
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 2:
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 1:
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            default:
                break;
        }
    }

    private int calculateGroupSize(long maxBlockSize, long globalWorkSize) {
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
        return value;
    }

    private long[] calculateEffectiveMaxWorkItemSizes(TaskMetaData metaData) {
        long[] intermediates = new long[] { 1, 1, 1 };

        long[] maxWorkItemSizes = deviceContext.getSPIRVDevice().getDeviceMaxWorkItemSizes();

        switch (metaData.getDims()) {
            case 3:
                intermediates[2] = (long) Math.sqrt(maxWorkItemSizes[2]);
                intermediates[1] = (long) Math.sqrt(maxWorkItemSizes[1]);
                intermediates[0] = (long) Math.sqrt(maxWorkItemSizes[0]);
                break;
            case 2:
                intermediates[1] = (long) Math.sqrt(maxWorkItemSizes[1]);
                intermediates[0] = (long) Math.sqrt(maxWorkItemSizes[0]);
                break;
            case 1:
                intermediates[0] = maxWorkItemSizes[0];
                break;
            default:
                break;

        }
        return intermediates;
    }

    private void calculateGlobalWork(final TaskMetaData meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            if (ADJUST_IRREGULAR && (value % WARP_SIZE != 0)) {
                value = ((value / WARP_SIZE) + 1) * WARP_SIZE;
            }
            globalWork[i] = value;
        }
    }

    private void checkLocalWorkGroupFitsOnDevice(final TaskMetaData meta) {
        WorkerGrid grid = meta.getWorkerGrid(meta.getId());
        long[] local = grid.getLocalWork();
        if (local != null) {
            LevelZeroGridInfo gridInfo = new LevelZeroGridInfo(deviceContext, local);
            boolean checkedDimensions = gridInfo.checkGridDimensions();
            if (!checkedDimensions) {
                System.out.println(WARNING_THREAD_LOCAL);
                grid.setLocalWorkToNull();
                grid.setNumberOfWorkgroupsToNull();
            }
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void invalidate() {
        valid = false;
    }
}
