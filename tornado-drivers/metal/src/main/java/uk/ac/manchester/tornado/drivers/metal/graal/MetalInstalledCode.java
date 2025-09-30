/*
 * Copyright (c) 2018, 2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.nio.ByteBuffer;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalKernel;
import uk.ac.manchester.tornado.drivers.metal.MetalProgram;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalByteBuffer;
import uk.ac.manchester.tornado.drivers.metal.mm.MetalKernelStackFrame;
import uk.ac.manchester.tornado.drivers.metal.runtime.MetalTornadoDevice;
import uk.ac.manchester.tornado.drivers.metal.scheduler.MetalGenericGPUScheduler;
import uk.ac.manchester.tornado.drivers.metal.scheduler.MetalKernelScheduler;
import uk.ac.manchester.tornado.drivers.metal.scheduler.MetalScheduler;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalInstalledCode extends InstalledCode implements TornadoInstalledCode {

    private static final int CL_MEM_SIZE = 8;
    private final MetalKernelScheduler DEFAULT_SCHEDULER;
    private final ByteBuffer buffer = ByteBuffer.allocate(CL_MEM_SIZE);
    private final byte[] code;
    private final MetalProgram program;
    private final MetalDeviceContext deviceContext;
    private final MetalKernel kernel;
    private final MetalKernelScheduler scheduler;
    private final int[] internalEvents = new int[1];
    private final long[] singleThreadGlobalWorkSize = new long[] { 1 };
    private final long[] singleThreadLocalWorkSize = new long[] { 1 };
    private final boolean isSPIRVBinary;
    private boolean valid;
    TornadoLogger logger = new TornadoLogger(this.getClass());

    public MetalInstalledCode(final String entryPoint, final byte[] code, final MetalDeviceContext deviceContext, final MetalProgram program, final MetalKernel kernel, boolean isSPIRVBinary) {
        super(entryPoint);
        this.code = code;
        this.deviceContext = deviceContext;
        this.scheduler = MetalScheduler.create(deviceContext);
        this.DEFAULT_SCHEDULER = new MetalGenericGPUScheduler(deviceContext);
        this.kernel = kernel;
        this.program = program;
        valid = kernel != null;
        buffer.order(deviceContext.getByteOrder());
        this.isSPIRVBinary = isSPIRVBinary;
        // If native reflection is enabled and debugging requested, print argument info
        if (kernel != null && TornadoOptions.FULL_DEBUG) {
            try {
                int argc = kernel.getArgCount();
                logger.debug("kernel reflection: %s args=%d", kernel.getName(), argc);
                for (int i = 0; i < argc; i++) {
                    MetalKernel.KernelArgInfo info = kernel.getArgInfoObject(i);
                    logger.debug("  arg[%d]=%s", i, (info != null) ? info.toString() : "<null>");
                }
            } catch (Exception e) {
                logger.error("unable to query kernel reflection: %s", e.getMessage());
            }
        }
    }

    @Override
    public void invalidate() {
        if (valid) {
            program.cleanup();
            valid = false;
        }
    }

    public MetalProgram getProgram() {
        return program;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public MetalKernel getKernel() {
        return kernel;
    }

    @Override
    public Object executeVarargs(final Object... args) throws InvalidInstalledCodeException {
        return null;
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    public String getGeneratedSourceCode() {
        return new String(code);
    }

    /**
     * Set arguments into the Metal device Kernel.
     *
     * @param kernelArgs
     *     Metal kernel parameters {@link MetalByteBuffer}
     * @param meta
     *     task metadata {@link TaskDataContext}
     */
    private void setKernelArgs(final MetalKernelStackFrame kernelArgs, final XPUBuffer atomicSpace, TaskDataContext meta) {
        int index = 0;

        // If native reflection is available, print/reflection info in debug mode
        if (kernel != null && TornadoOptions.FULL_DEBUG) {
            try {
                int argCount = kernel.getArgCount();
                logger.debug("Native kernel reflection: kernel=%s reflectedArgs=%d", kernel.getName(), argCount);
            } catch (Exception e) {
                logger.error("failed to query native kernel reflection: %s", e.getMessage());
            }
        }

        // kernel context
        if (DEBUG) {
            try {
                int argCount = (kernel != null) ? kernel.getArgCount() : 0;
                logger.info("[Metal-reflection] kernel=%s argCount=%d", (kernel != null ? kernel.getName() : "null"), argCount);
                for (int ai = 0; ai < argCount; ai++) {
                    MetalKernel.KernelArgInfo info = kernel.getArgInfoObject(ai);
                    logger.info("[Metal-reflection] arg[%d]=%s", ai, (info != null) ? info.toString() : "<null>");
                }
            } catch (Exception e) {
                logger.error("unable to query kernel reflection: %s", e.getMessage());
            }
        }

        buffer.clear();
        buffer.putLong(kernelArgs.toBuffer());
        kernel.setArg(index, buffer);
        index++;

        if (isSPIRVBinary) {
            // Set the rest of the SPIR-V kernel arguments.
            for (int i = 0, argIndex = 0; i < kernelArgs.getCallArguments().size(); i++) {
                KernelStackFrame.CallArgument arg = kernelArgs.getCallArguments().get(i);
                // Include the extra kernel context argument for SPIR-V binaries.
                if (arg.getValue() instanceof KernelStackFrame.KernelContextArgument) {
                    buffer.clear();
                    buffer.putLong(kernelArgs.toBuffer());
                    kernel.setArg(index + argIndex, buffer);
                    argIndex++;
                    continue;
                }
                if (isBoxedPrimitive(arg.getValue()) || arg.getValue().getClass().isPrimitive()) {
                    buffer.clear();
                    PrimitiveSerialiser.put(buffer, arg.getValue());
                    kernel.setArg(index + argIndex, buffer);
                } else {
                    shouldNotReachHere();
                }
                argIndex++;
            }
            return;
        }

        // constant memory
        if (meta != null && meta.getConstantSize() > 0) {
            kernel.setArg(index, ByteBuffer.wrap(meta.getConstantData()));
        } else {
            buffer.clear();
            buffer.putLong(kernelArgs.toConstantAddress());
            kernel.setArg(index, buffer);
        }
        index++;

        // local memory buffers
        // local memory buffers
        final int localIndex = index; // remember where the driver previously expected local region
        // We'll defer setting local regions until we inspect native reflection. If reflection
        // reports threadgroup arguments we will set local region at the per-argument native
        // index; otherwise fall back to the single local slot behaviour.
        index++;

    // Atomics in Global Memory
        buffer.clear();
        buffer.putLong(kernelArgs.toAtomicAddress());
        kernel.setArg(index, buffer);
        index++;

        // Parameters
        // Decide where to place local (threadgroup) regions: use native reflection when available
        boolean hasThreadgroupArgs = false;
        try {
            if (kernel != null) {
                for (MetalKernel.KernelArgInfo info : kernel.getArgInfoList()) {
                    if (info != null && info.type == MetalKernel.KernelArgInfo.ArgType.THREADGROUP) {
                        hasThreadgroupArgs = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore reflection failures and fall back to legacy behaviour
            hasThreadgroupArgs = false;
        }

        final int paramBaseIndex = index; // native arg index where parameters start

        // If no threadgroup args reported by reflection, preserve legacy single-slot behaviour
        if (!hasThreadgroupArgs) {
            if (meta != null && meta.getLocalSize() > 0) {
                logger.info("\tallocating %s of local memory", RuntimeUtilities.humanReadableByteCount(meta.getLocalSize(), true));
                kernel.setLocalRegion(localIndex, meta.getLocalSize());
            } else {
                kernel.setArgUnused(localIndex);
            }
        } else {
            // mark the legacy localIndex unused; actual local allocations will be set per-argument below
            kernel.setArgUnused(localIndex);
        }

        for (int i = 0, argIndex = 0; i < kernelArgs.getCallArguments().size(); i++) {
            KernelStackFrame.CallArgument arg = kernelArgs.getCallArguments().get(i);
            if (arg.getValue() instanceof KernelStackFrame.KernelContextArgument) {
                // We do not set any kernel context argument. This is only for the Java side.
                continue;
            }

            final int nativeArgIndex = paramBaseIndex + argIndex;

            // If native reflection indicates this argument is threadgroup memory, allocate local region here
            MetalKernel.KernelArgInfo argInfo = null;
            try {
                if (kernel != null) argInfo = kernel.getArgInfoObject(argIndex);
            } catch (Exception e) {
                // ignore reflection errors per-argument
            }

            if (argInfo != null && argInfo.type == MetalKernel.KernelArgInfo.ArgType.THREADGROUP) {
                if (meta != null && meta.getLocalSize() > 0) {
                    logger.info("\tallocating %s of threadgroup memory for arg %d", RuntimeUtilities.humanReadableByteCount(meta.getLocalSize(), true), i);
                    kernel.setLocalRegion(nativeArgIndex, meta.getLocalSize());
                } else {
                    kernel.setArgUnused(nativeArgIndex);
                }
                argIndex++;
                continue;
            }

            if (isBoxedPrimitive(arg.getValue()) || arg.getValue().getClass().isPrimitive()) {
                buffer.clear();
                PrimitiveSerialiser.put(buffer, arg.getValue());
                kernel.setArg(nativeArgIndex, buffer);
            } else {
                shouldNotReachHere();
            }
            argIndex++;
        }
    }

    private void printDebugLaunchInfo(final TaskDataContext meta) {
        System.out.println("Running on: ");
        System.out.println("\tPlatform: " + meta.getXPUDevice().getPlatformName());
        if (meta.getXPUDevice() instanceof MetalTornadoDevice) {
            System.out.println("\tDevice  : " + ((MetalTornadoDevice) meta.getXPUDevice()).getPhysicalDevice().getDeviceName());
        }
    }

    public int submitWithEvents(long executionPlanId, final MetalKernelStackFrame kernelArgs, final XPUBuffer atomicSpace, final TaskDataContext meta, final int[] events, long batchThreads) {
        guarantee(kernel != null, "kernel is null");

        if (DEBUG) {
            logger.info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have changed
         */
        final int[] waitEvents;
        setKernelArgs(kernelArgs, atomicSpace, meta);
        internalEvents[0] = kernelArgs.enqueueWrite(executionPlanId, events);
        waitEvents = internalEvents;
        long[] waitEventsLong = new long[waitEvents.length];
        for (int i = 0; i < waitEvents.length; i++) {
            waitEventsLong[i] = waitEvents[i];
        }
        updateProfilerKernelContextWrite(executionPlanId, internalEvents[0], meta, kernelArgs);

        int task;
        if (meta == null) {
            task = deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, waitEventsLong);
        } else {
            if (meta.isParallel()) {
                task = scheduler.submit(executionPlanId, kernel, meta, waitEventsLong, batchThreads);
            } else {
                if (meta.isDebug()) {
                    printDebugLaunchInfo(meta);
                }
                if (meta.getGlobalWork() == null) {
                    task = deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, waitEventsLong);
                } else {
                    task = deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, meta.getGlobalWork(), meta.getLocalWork(), waitEventsLong);
                }
            }
        }
        return task;
    }

    private void executeSingleThread(long executionPlanId) {
        deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, null);
    }

    private int submitSequential(long executionPlanId, final TaskDataContext meta) {
        final int task;

        System.out.println("DEBUG: submitSequential called");
        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }

        if ((meta.getGlobalWork() == null) || (meta.getGlobalWork().length == 0)) {
            // Sequential kernel execution
            task = deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, null);
        } else {
            // Ahead Of Time kernel execution
            task = deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, meta.getGlobalWork(), meta.getLocalWork(), null);
        }
        if (TornadoOptions.isProfilerEnabled()) {
            Event tornadoKernelEvent = deviceContext.resolveEvent(executionPlanId, task);
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
        return task;
    }

    private int submitParallel(long executionPlanId, final TaskDataContext meta, long batchThreads) {
        return scheduler.submit(executionPlanId, kernel, meta, batchThreads);
    }

    private void launchKernel(long executionPlanId, final MetalKernelStackFrame callWrapper, final TaskDataContext meta, long batchThreads) {
        if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            submitParallel(executionPlanId, meta, batchThreads);
        } else {
            submitSequential(executionPlanId, meta);
        }
    }

    private void checkKernelNotNull() {
        if (kernel == null) {
            throw new TornadoRuntimeException("[ERROR] Generated Kernel is NULL. \nPlease report this issue to https://github.com/beehive-lab/TornadoVM");
        }
    }

    private void submitWithoutEvents(long executionPlanId, final MetalKernelStackFrame oclKernelStackFrame, final XPUBuffer atomicSpace, final TaskDataContext meta, long batchThreads) {
        checkKernelNotNull();
        if (DEBUG) {
            logger.info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        }

        setKernelArgs(oclKernelStackFrame, atomicSpace, meta);
        int kernelContextWriteEventId = oclKernelStackFrame.enqueueWrite(executionPlanId);
        updateProfilerKernelContextWrite(executionPlanId, kernelContextWriteEventId, meta, oclKernelStackFrame);

        if (meta == null) {
            executeSingleThread(executionPlanId);
        } else {
            launchKernel(executionPlanId, oclKernelStackFrame, meta, batchThreads);
        }
    }

    private void updateProfilerKernelContextWrite(long executionPlanId, int kernelContextWriteEventId, TaskDataContext meta, MetalKernelStackFrame callWrapper) {
        if (TornadoOptions.isProfilerEnabled()) {
            TornadoProfiler profiler = meta.getProfiler();
            Event event = deviceContext.resolveEvent(executionPlanId, kernelContextWriteEventId);
            event.waitForEvents(executionPlanId);
            long copyInTimer = meta.getProfiler().getTimer(ProfilerType.COPY_IN_TIME);
            copyInTimer += event.getElapsedTime();
            profiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);
            profiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, meta.getId(), callWrapper.getSize());

            long dispatchValue = profiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            profiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
    }

    @Override
    public int launchWithDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads, int[] waitEvents) {
        return submitWithEvents(executionPlanId, (MetalKernelStackFrame) callWrapper, atomicSpace, meta, waitEvents, batchThreads);
    }

    @Override
    public int launchWithoutDependencies(long executionPlanId, KernelStackFrame callWrapper, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads) {
        submitWithoutEvents(executionPlanId, (MetalKernelStackFrame) callWrapper, atomicSpace, meta, batchThreads);
        return -1;
    }
}
