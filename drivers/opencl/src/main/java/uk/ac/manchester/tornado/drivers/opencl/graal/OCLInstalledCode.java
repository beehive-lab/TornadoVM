/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLGPUScheduler;
import uk.ac.manchester.tornado.drivers.opencl.OCLKernel;
import uk.ac.manchester.tornado.drivers.opencl.OCLKernelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.OCLProgram;
import uk.ac.manchester.tornado.drivers.opencl.OCLScheduler;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLCallStack;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.CallStack;
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

    /**
     * It executes a kernel with 1 thread (the equivalent of calling clEnqueueTask.
     *
     * @param stack
     *            {@link OCLByteBuffer} stack
     * @param meta
     *            {@link TaskMetaData} netadata
     * @return int with the event ID.
     */
    public int executeTask(final OCLByteBuffer stack, final ObjectBuffer atomicSpace, final TaskMetaData meta) {
        debug("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        debug("\tstack    : buffer id=0x%x, address=0x%x relative=0x%x", stack.toBuffer(), stack.toAbsoluteAddress(), stack.toRelativeAddress());

        setKernelArgs(stack, atomicSpace, meta);

        int task;
        if (meta == null) {
            task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, internalEvents);
            deviceContext.flush();
            deviceContext.finish();
        } else {
            if (meta.isParallel()) {
                if (meta.enableThreadCoarsener()) {
                    task = DEFAULT_SCHEDULER.submit(kernel, meta, null, 0);
                } else {
                    task = scheduler.submit(kernel, meta, null, 0);
                }
            } else {
                task = deviceContext.enqueueNDRangeKernel(kernel, 1, null, singleThreadGlobalWorkSize, singleThreadLocalWorkSize, null);
            }
        }
        return task;
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
     * @param stack
     *            OpenCL stack parameters {@link OCLByteBuffer}
     * @param meta
     *            task metadata {@link TaskMetaData}
     */
    private void setKernelArgs(final OCLByteBuffer stack, final ObjectBuffer atomicSpace, TaskMetaData meta) {
        int index = 0;

        if (deviceContext.needsBump()) {
            buffer.clear();
            buffer.putLong(deviceContext.getBumpBuffer());
            kernel.setArg(index, buffer);
            index++;
        }

        // heap (global memory)
        buffer.clear();
        buffer.putLong(stack.toBuffer());
        kernel.setArg(index, buffer);
        index++;

        // stack pointer
        buffer.clear();
        buffer.putLong(stack.toRelativeAddress());
        kernel.setArg(index, buffer);
        index++;

        if (isSPIRVBinary) {
            return;
        }

        // constant memory
        if (meta != null && meta.getConstantSize() > 0) {
            kernel.setArg(index, ByteBuffer.wrap(meta.getConstantData()));
        } else {
            buffer.clear();
            buffer.putLong(stack.toConstantAddress());
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
        buffer.putLong(stack.toAtomicAddress());
        kernel.setArg(index, buffer);
        index++;

    }

    public int submitWithEvents(final OCLCallStack stack, final ObjectBuffer atomicSpace, final TaskMetaData meta, final int[] events, long batchThreads) {
        guarantee(kernel != null, "kernel is null");

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
            info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(), stack.toAbsoluteAddress(), stack.toRelativeAddress());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have changed
         */
        final int[] waitEvents;
        if (!stack.isOnDevice()) {
            setKernelArgs(stack, atomicSpace, meta);
            internalEvents[0] = stack.enqueueWrite(events);
            waitEvents = internalEvents;
            updateProfilerStackWrite(internalEvents[0], meta, stack);
        } else {
            waitEvents = events;
        }

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
                task = stack.enqueueRead(internalEvents);
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

    private void launchKernel(final OCLCallStack stack, final TaskMetaData meta, long batchThreads) {
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
            stack.enqueueRead(null);
        }
    }

    private void checkKernelNotNull() {
        if (kernel == null) {
            throw new TornadoRuntimeException("[ERROR] Generated Kernel is NULL. \nPlease report this issue to https://github.com/beehive-lab/TornadoVM");
        }
    }

    private void submitWithoutEvents(final OCLCallStack stack, final ObjectBuffer atomicSpace, final TaskMetaData meta, long batchThreads) {

        checkKernelNotNull();

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
            info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(), stack.toAbsoluteAddress(), stack.toRelativeAddress());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have changed
         */
        if (!stack.isOnDevice()) {
            setKernelArgs(stack, atomicSpace, meta);
            int stackWriteEventId = stack.enqueueWrite();
            updateProfilerStackWrite(stackWriteEventId, meta, stack);
        }

        guarantee(kernel != null, "kernel is null");
        if (meta == null) {
            executeSingleThread();
        } else {
            launchKernel(stack, meta, batchThreads);
        }
    }

    private void updateProfilerStackWrite(int stackWriteEventId, TaskMetaData meta, OCLCallStack stack) {
        if (TornadoOptions.isProfilerEnabled()) {
            TornadoProfiler profiler = meta.getProfiler();
            Event event = deviceContext.resolveEvent(stackWriteEventId);
            event.waitForEvents();
            long copyInTimer = meta.getProfiler().getTimer(ProfilerType.COPY_IN_TIME);
            copyInTimer += event.getElapsedTime();
            profiler.setTimer(ProfilerType.COPY_IN_TIME, copyInTimer);
            profiler.addValueToMetric(ProfilerType.TOTAL_COPY_IN_SIZE_BYTES, meta.getId(), stack.getSize());

            long dispatchValue = profiler.getTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME);
            dispatchValue += event.getDriverDispatchTime();
            profiler.setTimer(ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME, dispatchValue);
        }
    }

    @Override
    public int launchWithDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        return submitWithEvents((OCLCallStack) stack, atomicSpace, meta, waitEvents, batchThreads);
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        submitWithoutEvents((OCLCallStack) stack, atomicSpace, meta, batchThreads);
        return -1;
    }

}
