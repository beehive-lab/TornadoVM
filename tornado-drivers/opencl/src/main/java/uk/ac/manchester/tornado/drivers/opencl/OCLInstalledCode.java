/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.COPY_IN_TIME;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TASK_KERNEL_TIME;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_COPY_IN_SIZE_BYTES;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_DISPATCH_DATA_TRANSFERS_TIME;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_DISPATCH_KERNEL_TIME;
import static uk.ac.manchester.tornado.api.profiler.ProfilerType.TOTAL_KERNEL_TIME;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEBUG;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelStackFrame;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.drivers.opencl.scheduler.OCLKernelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.scheduler.OCLScheduler;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * An OpenCL kernel built for one device, ready to launch: it sets the kernel's arguments from a
 * {@link OCLKernelStackFrame}, writes the frame to the device and enqueues the NDRange.
 *
 * <p>
 * Argument order for kernels compiled from OpenCL C: the stack frame, the constant region, the
 * local region, the global atomics region, then the task's scalar parameters. SPIR-V kernels take
 * the stack frame followed directly by the parameters, with the frame passed again wherever the
 * task has a {@code KernelContext}.
 * </p>
 */
public class OCLInstalledCode implements TornadoInstalledCode {

    private final TornadoLogger logger = new TornadoLogger(OCLInstalledCode.class);

    private final String name;
    private final byte[] code;
    private final OCLDeviceContext deviceContext;
    private final OCLProgram program;
    private final OCLKernel kernel;
    private final OCLKernelScheduler scheduler;
    private final boolean isSPIRVBinary;
    private boolean valid;

    /** Staging for one by-value argument: every argument fits in 8 bytes. */
    private final ByteBuffer argument = ByteBuffer.allocate(Long.BYTES);
    /** The frame-write event a dependent launch waits on. */
    private final int[] frameWritten = new int[1];
    /** Global and local size of a single-work-item launch. */
    private final long[] oneWorkItem = { 1 };

    public OCLInstalledCode(String entryPoint, byte[] code, OCLDeviceContext deviceContext, OCLProgram program, OCLKernel kernel, boolean isSPIRVBinary) {
        this.name = entryPoint;
        this.code = code;
        this.deviceContext = deviceContext;
        this.program = program;
        this.kernel = kernel;
        this.isSPIRVBinary = isSPIRVBinary;
        this.scheduler = OCLScheduler.create(deviceContext);
        this.valid = kernel != null;
        argument.order(deviceContext.getByteOrder());
    }

    public String getName() {
        return name;
    }

    public byte[] getCode() {
        return code;
    }

    public String getGeneratedSourceCode() {
        return new String(code, StandardCharsets.UTF_8);
    }

    public OCLProgram getProgram() {
        return program;
    }

    public OCLKernel getKernel() {
        return kernel;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void invalidate() {
        if (valid) {
            program.cleanup();
            valid = false;
        }
    }

    @Override
    public int launchWithDependencies(long executionPlanId, KernelStackFrame frame, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads, int[] waitEvents) {
        return submitWithEvents(executionPlanId, (OCLKernelStackFrame) frame, atomicSpace, meta, waitEvents, batchThreads);
    }

    @Override
    public int launchWithoutDependencies(long executionPlanId, KernelStackFrame frame, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads) {
        submitWithoutEvents(executionPlanId, (OCLKernelStackFrame) frame, meta, batchThreads);
        return -1;
    }

    /**
     * Launches after {@code events}: the frame write waits on them and the kernel waits on the frame
     * write.
     *
     * @return the kernel's event
     */
    public int submitWithEvents(long executionPlanId, OCLKernelStackFrame frame, XPUBuffer atomicSpace, TaskDataContext meta, int[] events, long batchThreads) {
        guarantee(kernel != null, "kernel is null");
        logSubmission();
        setKernelArgs(frame, meta);
        frameWritten[0] = frame.enqueueWrite(executionPlanId, events);
        recordFrameWrite(executionPlanId, frameWritten[0], meta, frame);

        if (meta == null) {
            return enqueue(executionPlanId, oneWorkItem, oneWorkItem, frameWritten);
        }
        if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            return scheduler.submit(executionPlanId, kernel, meta, frameWritten, batchThreads);
        }
        if (meta.isDebug()) {
            printDeviceInfo(meta);
        }
        return meta.getGlobalWork() == null //
                ? enqueue(executionPlanId, oneWorkItem, oneWorkItem, frameWritten)
                : enqueue(executionPlanId, meta.getGlobalWork(), meta.getLocalWork(), frameWritten);
    }

    private void submitWithoutEvents(long executionPlanId, OCLKernelStackFrame frame, TaskDataContext meta, long batchThreads) {
        if (kernel == null) {
            throw new TornadoRuntimeException("[ERROR] Generated Kernel is NULL. \nPlease report this issue to https://github.com/beehive-lab/TornadoVM");
        }
        logSubmission();
        setKernelArgs(frame, meta);
        recordFrameWrite(executionPlanId, frame.enqueueWrite(executionPlanId), meta, frame);

        if (meta == null) {
            enqueue(executionPlanId, oneWorkItem, oneWorkItem, null);
        } else if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            scheduler.submit(executionPlanId, kernel, meta, batchThreads);
        } else {
            submitSequential(executionPlanId, meta);
        }
    }

    /** A task without a parallel loop or worker grid: one work item, or the ahead-of-time sizes when the task has them. */
    private void submitSequential(long executionPlanId, TaskDataContext meta) {
        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }
        long[] global = meta.getGlobalWork();
        int kernelEvent = global == null || global.length == 0 //
                ? enqueue(executionPlanId, oneWorkItem, oneWorkItem, null)
                : enqueue(executionPlanId, global, meta.getLocalWork(), null);
        if (TornadoOptions.isProfilerEnabled()) {
            recordKernelTime(executionPlanId, kernelEvent, meta);
        }
    }

    private int enqueue(long executionPlanId, long[] global, long[] local, int[] waitEvents) {
        return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, global, local, waitEvents);
    }

    private void setKernelArgs(OCLKernelStackFrame frame, TaskDataContext meta) {
        int index = 0;
        setAddressArg(index++, frame.toBuffer());

        if (isSPIRVBinary) {
            for (KernelStackFrame.CallArgument arg : frame.getCallArguments()) {
                if (arg.getValue() instanceof KernelStackFrame.KernelContextArgument) {
                    setAddressArg(index++, frame.toBuffer());
                } else {
                    setValueArg(index++, arg.getValue());
                }
            }
            return;
        }

        if (meta != null && meta.getConstantSize() > 0) {
            kernel.setArg(index++, ByteBuffer.wrap(meta.getConstantData()));
        } else {
            setAddressArg(index++, frame.toConstantAddress());
        }

        if (meta != null && meta.getLocalSize() > 0) {
            logger.info("\tallocating %s of local memory", RuntimeUtilities.humanReadableByteCount(meta.getLocalSize(), true));
            kernel.setLocalRegion(index++, meta.getLocalSize());
        } else {
            kernel.setArgUnused(index++);
        }

        setAddressArg(index++, frame.toAtomicAddress());

        // The KernelContext exists only on the Java side: OpenCL C kernels read work-item ids directly.
        for (KernelStackFrame.CallArgument arg : frame.getCallArguments()) {
            if (!(arg.getValue() instanceof KernelStackFrame.KernelContextArgument)) {
                setValueArg(index++, arg.getValue());
            }
        }
    }

    private void setAddressArg(int index, long address) {
        argument.clear();
        argument.putLong(address);
        kernel.setArg(index, argument);
    }

    private void setValueArg(int index, Object value) {
        if (!isBoxedPrimitive(value) && !value.getClass().isPrimitive()) {
            throw shouldNotReachHere();
        }
        argument.clear();
        PrimitiveSerialiser.put(argument, value);
        kernel.setArg(index, argument);
    }

    private void logSubmission() {
        if (DEBUG) {
            logger.info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getOclKernelID(), kernel.getName(), deviceContext.getDevice().getDeviceName());
        }
    }

    private static void printDeviceInfo(TaskDataContext meta) {
        System.out.println("Running on: ");
        System.out.println("\tPlatform: " + meta.getXPUDevice().getPlatformName());
        if (meta.getXPUDevice() instanceof OCLTornadoDevice device) {
            System.out.println("\tDevice  : " + device.getPhysicalDevice().getDeviceName());
        }
    }

    private void recordFrameWrite(long executionPlanId, int eventId, TaskDataContext meta, OCLKernelStackFrame frame) {
        if (!TornadoOptions.isProfilerEnabled()) {
            return;
        }
        var event = deviceContext.resolveEvent(executionPlanId, eventId);
        event.waitForEvents(executionPlanId);
        var profiler = meta.getProfiler();
        profiler.setTimer(COPY_IN_TIME, profiler.getTimer(COPY_IN_TIME) + event.getElapsedTime());
        profiler.addValueToMetric(TOTAL_COPY_IN_SIZE_BYTES, meta.getId(), frame.getSize());
        profiler.setTimer(TOTAL_DISPATCH_DATA_TRANSFERS_TIME, profiler.getTimer(TOTAL_DISPATCH_DATA_TRANSFERS_TIME) + event.getDriverDispatchTime());
    }

    private void recordKernelTime(long executionPlanId, int eventId, TaskDataContext meta) {
        var event = deviceContext.resolveEvent(executionPlanId, eventId);
        event.waitForEvents(executionPlanId);
        long elapsed = event.getElapsedTime();
        var profiler = meta.getProfiler();
        profiler.setTimer(TOTAL_KERNEL_TIME, profiler.getTimer(TOTAL_KERNEL_TIME) + elapsed);
        profiler.setTaskTimer(TASK_KERNEL_TIME, meta.getId(), elapsed);
        profiler.setTimer(TOTAL_DISPATCH_KERNEL_TIME, profiler.getTimer(TOTAL_DISPATCH_KERNEL_TIME) + event.getDriverDispatchTime());
    }
}
