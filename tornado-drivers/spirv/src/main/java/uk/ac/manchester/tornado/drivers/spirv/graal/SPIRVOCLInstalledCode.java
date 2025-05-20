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
import uk.ac.manchester.tornado.runtime.common.UpsMeterReader;
import uk.ac.manchester.tornado.drivers.common.utils.EventDescriptor;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLErrorCode;
import uk.ac.manchester.tornado.drivers.opencl.OCLEventPool;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVKernelStackFrame;
import uk.ac.manchester.tornado.drivers.spirv.ocl.SPIRVOCLNativeDispatcher;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

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
    public int launchWithDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads, int[] waitEvents) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public int launchWithoutDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads) {

        // Set kernel args
        setKernelArgs(executionPlanId, (SPIRVKernelStackFrame) callWrapper);
        SPIRVOCLModule module = (SPIRVOCLModule) spirvModule;
        long kernelPointer = module.getKernelPointer();

        // Calculate the GWS and LWS
        calculateGlobalAndLocalBlockOfThreads(meta, batchThreads);

        submit(executionPlanId, kernelPointer, meta, null);
        return -1;
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
                    // In OpenCL, we need to set the argument. But it is set as buffer pointer. So we add the kernelContext as a dummy one.
                    status = dispatcher.clSetKernelArg(kernelPointer, kernelParamIndex, Sizeof.LONG.getNumBytes(), callWrapper.toBuffer());
                } else {
                    status = dispatcher.clSetKernelArg(kernelPointer, kernelParamIndex, Sizeof.LONG.getNumBytes(), ((Number) arg.getValue()).longValue());
                }
                checkStatus(status, "clSetKernelArg");
            } else {
                TornadoInternalError.shouldNotReachHere();
            }
        }
    }

    public void submit(long executionPlanId, long kernelPointer, final TaskDataContext meta, long[] waitEvents) {
        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }
        long[] kernelEvent = new long[1];
        final int status = launch(executionPlanId, kernelPointer, meta, waitEvents, kernelEvent);
        if (status != OCLErrorCode.CL_SUCCESS) {
            switch (status) {
                case OCLErrorCode.CL_INVALID_KERNEL_ARGS -> System.err.println("[OCL Error] Invalid Kernel Args");
                case OCLErrorCode.CL_INVALID_WORK_GROUP_SIZE -> System.err.println("[OCL Error] Invalid Work Group Size");
            }
        }

        OCLCommandQueue commandQueue = (OCLCommandQueue) deviceContext.getSpirvContext().getCommandQueueForDevice(executionPlanId, deviceContext.getDeviceIndex());
        OCLEventPool eventPool = deviceContext.getSpirvContext().getOCLEventPool(executionPlanId);
        int value = eventPool.registerEvent(kernelEvent[0], EventDescriptor.DESC_PARALLEL_KERNEL, commandQueue);
        updateProfiler(executionPlanId, value, meta);
    }

    public int launch(long executionPlanId, long kernelPointer, final TaskDataContext meta, long[] waitEvents, long[] kernelEvent) {
        SPIRVOCLNativeDispatcher dispatcher = new SPIRVOCLNativeDispatcher();
        OCLCommandQueue commandQueue = (OCLCommandQueue) deviceContext.getSpirvContext().getCommandQueueForDevice(executionPlanId, deviceContext.getDeviceIndex());
        long queuePointer = commandQueue.getCommandQueuePtr();

        if (meta.isWorkerGridAvailable()) {
            WorkerGrid workerGrid = meta.getWorkerGrid(meta.getId());
            assert workerGrid != null;
            return dispatcher.clEnqueueNDRangeKernel(queuePointer, kernelPointer, //
                    workerGrid.dimension(), workerGrid.getGlobalOffset(), workerGrid.getGlobalWork(), workerGrid.getLocalWork(),//
                    waitEvents, kernelEvent);//
        } else {
            long[] localWorkGroup = meta.getLocalWork();
            if (!meta.shouldUseOpenCLDriverScheduling()) {
                localWorkGroup = meta.getLocalWork();
            }
            return dispatcher.clEnqueueNDRangeKernel(queuePointer, kernelPointer, //
                    meta.getDims(), meta.getGlobalOffset(), meta.getGlobalWork(), //
                    localWorkGroup, waitEvents, kernelEvent);//
        }
    }

    private void updateProfiler(long executionPlanId, final int taskEvent, final TaskDataContext meta) {
        if (TornadoOptions.isProfilerEnabled()) {
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

    private void calculateGlobalAndLocalBlockOfThreads(TaskDataContext meta, long batchThreads) {
        long[] gwg = new long[] { 1, 1, 1 };
        long[] lwg = new long[] { 1, 1, 1 };

        if (!meta.isGridSchedulerEnabled()) {
            int dims = meta.getDims();
            if (!meta.isGlobalWorkDefined()) {
                calculateGlobalWork(meta, batchThreads);
            }
            if (!meta.isLocalWorkDefined()) {
                calculateLocalWork(meta);
            }
            System.arraycopy(meta.getGlobalWork(), 0, gwg, 0, dims);
            System.arraycopy(meta.getLocalWork(), 0, lwg, 0, dims);
        } else {
            checkLocalWorkGroupFitsOnDevice(meta);
            WorkerGrid worker = meta.getWorkerGrid(meta.getId());
            System.arraycopy(worker.getGlobalWork(), 0, gwg, 0, gwg.length);
            if (worker.getLocalWork() != null) {
                System.arraycopy(worker.getLocalWork(), 0, lwg, 0, lwg.length);
            }
        }
    }

    public void calculateLocalWork(final TaskDataContext meta) {
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

    private long[] calculateEffectiveMaxWorkItemSizes(TaskDataContext metaData) {
        long[] intermediates = new long[] { 1, 1, 1 };

        long[] maxWorkItemSizes = deviceContext.getDevice().getDeviceMaxWorkItemSizes();

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

    private void calculateGlobalWork(final TaskDataContext meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            if (ADJUST_IRREGULAR && (value % WARP_SIZE != 0)) {
                value = ((value / WARP_SIZE) + 1) * WARP_SIZE;
            }
            globalWork[i] = value;
        }
    }

    private void checkLocalWorkGroupFitsOnDevice(final TaskDataContext meta) {
        WorkerGrid grid = meta.getWorkerGrid(meta.getId());
        long[] local = grid.getLocalWork();
        if (local != null) {
            long[] blockMaxWorkGroupSize = deviceContext.getDevice().getDeviceMaxWorkGroupSize();
            long maxWorkGroupSize = Arrays.stream(blockMaxWorkGroupSize).sum();
            long totalThreads = Arrays.stream(local).reduce(1, (a, b) -> a * b);
            boolean checkedDimensions = totalThreads <= maxWorkGroupSize;

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
