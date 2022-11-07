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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;
import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.nio.ByteBuffer;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLGPUScheduler;
import uk.ac.manchester.tornado.drivers.opencl.OCLKernel;
import uk.ac.manchester.tornado.drivers.opencl.OCLKernelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.OCLProgram;
import uk.ac.manchester.tornado.drivers.opencl.OCLScheduler;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelArgs;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLInstalledCode extends InstalledCode implements TornadoInstalledCode {

    private static final int CL_MEM_SIZE = 8;
    private final OCLKernelScheduler DEFAULT_SCHEDULER;
    private final ByteBuffer buffer = ByteBuffer.allocate(CL_MEM_SIZE);
    private final byte[] code;
    private final OCLProgram program;
    private final OCLDeviceContext deviceContext;
    private final OCLKernel kernel;
    private final OCLKernelScheduler scheduler;
    private final int[] internalEvents = new int[1];
    private final long[] singleThreadGlobalWorkSize = new long[] { 1 };
    private final long[] singleThreadLocalWorkSize = new long[] { 1 };
    private final boolean isSPIRVBinary;
    private boolean valid;

    public OCLInstalledCode(final String entryPoint, final byte[] code, final OCLDeviceContext deviceContext, final OCLProgram program, final OCLKernel kernel, boolean isSPIRVBinary) {
        super(entryPoint);
        this.code = code;
        this.deviceContext = deviceContext;
        this.scheduler = OCLScheduler.create(deviceContext);
        this.DEFAULT_SCHEDULER = new OCLGPUScheduler(deviceContext);
        this.kernel = kernel;
        this.program = program;
        valid = kernel != null;
        buffer.order(deviceContext.getByteOrder());
        this.isSPIRVBinary = isSPIRVBinary;
    }

    @Override
    public void invalidate() {
        if (valid) {
            kernel.cleanup();
            valid = false;
        }
    }

    public OCLProgram getProgram() {
        return program;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public OCLKernel getKernel() {
        return kernel;
    }

    /**
     * stack needs to be read so that the return value is transferred back to the
     * host.- As this is blocking then no clFinish() is needed
     */
    public void readValue(final OCLByteBuffer stack, final TaskMetaData meta, int task) {
        stack.read();
    }

    public void resolveEvent(final OCLByteBuffer stack, final TaskMetaData meta, int task) {
        Event event = deviceContext.resolveEvent(task);
        debug("kernel completed: id=0x%x, method = %s, device = %s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        if (event != null) {
            debug("\tstatus   : %s", event.getStatus());

            if (meta != null && meta.enableProfiling()) {
                debug("\texecuting: %f seconds", event.getElapsedTimeInSeconds());
                debug("\ttotal    : %f seconds", event.getTotalTimeInSeconds());
            }
        }
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
     * Set arguments into the OpenCL device Kernel.
     *
     * @param kernelArgs
     *            OpenCL kernel parameters {@link OCLByteBuffer}
     * @param meta
     *            task metadata {@link TaskMetaData}
     */
    private void setKernelArgs(final OCLKernelArgs kernelArgs, final ObjectBuffer atomicSpace, TaskMetaData meta) {
        int index = 0;

        if (deviceContext.needsBump()) {
            buffer.clear();
            buffer.putLong(deviceContext.getBumpBuffer());
            kernel.setArg(index, buffer);
            index++;
        }

        // kernel context
        buffer.clear();
        buffer.putLong(kernelArgs.toBuffer());
        kernel.setArg(index, buffer);
        index++;

        if (isSPIRVBinary) {
            // Set the rest of the SPIR-V kernel arguments.
            for (int i = 0, argIndex = 0; i < kernelArgs.getCallArguments().size(); i++) {
                KernelArgs.CallArgument arg = kernelArgs.getCallArguments().get(i);
                // Include the extra kernel context argument for SPIR-V binaries.
                if (arg.getValue() instanceof KernelArgs.KernelContextArgument) {
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

        // local memory
        if (meta != null && meta.getLocalSize() > 0) {
            info("\tallocating %s of local memory", RuntimeUtilities.humanReadableByteCount(meta.getLocalSize(), true));
            kernel.setLocalRegion(index, meta.getLocalSize());
        } else {
            kernel.setArgUnused(index);
        }
        index++;

        // Atomics in Global Memory
        buffer.clear();
        buffer.putLong(kernelArgs.toAtomicAddress());
        kernel.setArg(index, buffer);
        index++;

        // Parameters
        for (int i = 0, argIndex = 0; i < kernelArgs.getCallArguments().size(); i++) {
            KernelArgs.CallArgument arg = kernelArgs.getCallArguments().get(i);
            if (arg.getValue() instanceof KernelArgs.KernelContextArgument) {
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
    }

    public int submitWithEvents(final OCLKernelArgs kernelArgs, final ObjectBuffer atomicSpace, final TaskMetaData meta, final int[] events, long batchThreads) {
        guarantee(kernel != null, "kernel is null");

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have changed
         */
        final int[] waitEvents;
        setKernelArgs(kernelArgs, atomicSpace, meta);
        internalEvents[0] = kernelArgs.enqueueWrite(events);
        waitEvents = internalEvents;
        updateProfilerKernelContextWrite(internalEvents[0], meta, kernelArgs);

        int task;
        if (meta == null) {
            task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, waitEvents);
        } else {
            if (meta.isParallel()) {
                if (meta.enableThreadCoarsener()) {
                    task = DEFAULT_SCHEDULER.submit(kernel, meta, waitEvents, batchThreads);
                } else {
                    task = scheduler.submit(kernel, meta, waitEvents, batchThreads);
                }
            } else {
                if (meta.isDebug()) {
                    System.out.println("Running on: ");
                    System.out.println("\tPlatform: " + meta.getLogicDevice().getPlatformName());
                    if (meta.getLogicDevice() instanceof OCLTornadoDevice) {
                        System.out.println("\tDevice  : " + ((OCLTornadoDevice) meta.getLogicDevice()).getPhysicalDevice().getDeviceName());
                    }
                }
                if (meta.getGlobalWork() == null) {
                    task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, waitEvents);
                } else {
                    task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, meta.getGlobalWork(), meta.getLocalWork(), waitEvents);
                }
            }

            if (meta.shouldDumpProfiles()) {
                deviceContext.retainEvent(task);
                meta.addProfile(task);
            }

            if (meta.enableExceptions()) {
                internalEvents[0] = task;
                task = kernelArgs.enqueueRead(internalEvents);
            }
        }

        return task;
    }

    private void executeSingleThread() {
        deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, null);
    }

    private void debugInfo(final TaskMetaData meta) {
        if (meta.isDebug()) {
            meta.printThreadDims();
        }
    }

    private int submitSequential(final TaskMetaData meta) {
        final int task;
        debugInfo(meta);

        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }

        if ((meta.getGlobalWork() == null) || (meta.getGlobalWork().length == 0)) {
            // Sequential kernel execution
            task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, null);
        } else {
            // Ahead Of Time kernel execution
            task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, meta.getGlobalWork(), meta.getLocalWork(), null);
        }
        if (TornadoOptions.isProfilerEnabled()) {
            Event tornadoKernelEvent = deviceContext.resolveEvent(task);
            tornadoKernelEvent.waitForEvents();
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

    private int submitParallel(final TaskMetaData meta, long batchThreads) {
        final int task;
        if (meta.enableThreadCoarsener()) {
            task = DEFAULT_SCHEDULER.submit(kernel, meta, batchThreads);
        } else {
            task = scheduler.submit(kernel, meta, batchThreads);
        }
        return task;
    }

    private void launchKernel(final OCLKernelArgs callWrapper, final TaskMetaData meta, long batchThreads) {
        final int task;
        if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            task = submitParallel(meta, batchThreads);
        } else {
            task = submitSequential(meta);
        }

        if (meta.shouldDumpProfiles()) {
            deviceContext.retainEvent(task);
            meta.addProfile(task);
        }

        // read the stack
        if (meta.enableExceptions()) {
            callWrapper.enqueueRead(null);
        }
    }

    private void checkKernelNotNull() {
        if (kernel == null) {
            throw new TornadoRuntimeException("[ERROR] Generated Kernel is NULL. \nPlease report this issue to https://github.com/beehive-lab/TornadoVM");
        }
    }

    private void submitWithoutEvents(final OCLKernelArgs callWrapper, final ObjectBuffer atomicSpace, final TaskMetaData meta, long batchThreads) {

        checkKernelNotNull();

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        }

        setKernelArgs(callWrapper, atomicSpace, meta);
        int kernelContextWriteEventId = callWrapper.enqueueWrite();
        updateProfilerKernelContextWrite(kernelContextWriteEventId, meta, callWrapper);

        guarantee(kernel != null, "kernel is null");
        if (meta == null) {
            executeSingleThread();
        } else {
            launchKernel(callWrapper, meta, batchThreads);
        }
    }

    private void updateProfilerKernelContextWrite(int kernelContextWriteEventId, TaskMetaData meta, OCLKernelArgs callWrapper) {
        if (TornadoOptions.isProfilerEnabled()) {
            TornadoProfiler profiler = meta.getProfiler();
            Event event = deviceContext.resolveEvent(kernelContextWriteEventId);
            event.waitForEvents();
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
    public int launchWithDependencies(KernelArgs callWrapper, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        return submitWithEvents((OCLKernelArgs) callWrapper, atomicSpace, meta, waitEvents, batchThreads);
    }

    @Override
    public int launchWithoutDependencies(KernelArgs callWrapper, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        submitWithoutEvents((OCLKernelArgs) callWrapper, atomicSpace, meta, batchThreads);
        return -1;
    }

}
