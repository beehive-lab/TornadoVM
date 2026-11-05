/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda;

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
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAKernelStackFrame;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.drivers.cuda.scheduler.CUDAKernelScheduler;
import uk.ac.manchester.tornado.drivers.cuda.scheduler.CUDAScheduler;
import uk.ac.manchester.tornado.drivers.cuda.scheduler.CUDATileScheduler;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * A CUDA kernel loaded for one device, ready to launch: it sets the kernel's arguments from a
 * {@link CUDAKernelStackFrame}, writes the frame to the device and launches the grid.
 *
 * <p>
 * Argument order for kernels compiled from CUDA C: the stack frame, the constant region, the local
 * region, the global atomics region, then the task's scalar parameters. SPIR-V kernels take the
 * stack frame followed directly by the parameters, with the frame passed again wherever the task
 * has a {@code KernelContext}.
 * </p>
 *
 * <p>
 * While a stream is being captured into a CUDA graph the frame write is deferred to outside the
 * graph (the frame is constant across replays), and no event recorded by the capture is waited on.
 * </p>
 */
public class CUDAInstalledCode implements TornadoInstalledCode {

    private final TornadoLogger logger = new TornadoLogger(CUDAInstalledCode.class);

    private final String name;
    private final byte[] code;
    private final CUDADeviceContext deviceContext;
    private final CUDAProgram program;
    private final CUDAKernel kernel;
    private final CUDAKernelScheduler scheduler;
    private final boolean isSPIRVBinary;
    private boolean valid;
    private List<String> deviceLaunchTree = List.of();

    /** Staging for one by-value argument: every argument fits in 8 bytes. */
    private final ByteBuffer argument = ByteBuffer.allocate(Long.BYTES);
    /** The frame-write event a dependent launch waits on. */
    private final int[] frameWritten = new int[1];
    /** Grid and block size of a single-thread launch. */
    private final long[] oneThread = { 1 };

    public CUDAInstalledCode(String entryPoint, byte[] code, CUDADeviceContext deviceContext, CUDAProgram program, CUDAKernel kernel, boolean isSPIRVBinary) {
        this(entryPoint, code, deviceContext, program, kernel, isSPIRVBinary, false);
    }

    public CUDAInstalledCode(String entryPoint, byte[] code, CUDADeviceContext deviceContext, CUDAProgram program, CUDAKernel kernel, boolean isSPIRVBinary, boolean isTileKernel) {
        this.name = entryPoint;
        this.code = code;
        this.deviceContext = deviceContext;
        this.program = program;
        this.kernel = kernel;
        this.isSPIRVBinary = isSPIRVBinary;
        // A tile kernel launches one thread per block, which no general-purpose scheduler would
        // choose. Choosing per kernel rather than per device lets SIMT and tile tasks share a GPU.
        this.scheduler = isTileKernel ? new CUDATileScheduler(deviceContext) : CUDAScheduler.create(deviceContext);
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

    public CUDAProgram getProgram() {
        return program;
    }

    public CUDAKernel getKernel() {
        return kernel;
    }

    @Override
    public int maxThreadsPerBlock() {
        return kernel != null ? kernel.maxThreadsPerBlock() : Integer.MAX_VALUE;
    }

    public void setDeviceLaunchTree(List<String> tree) {
        deviceLaunchTree = List.copyOf(tree);
    }

    @Override
    public List<String> getDeviceLaunchTree() {
        return deviceLaunchTree;
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
        return submitWithEvents(executionPlanId, (CUDAKernelStackFrame) frame, atomicSpace, meta, waitEvents, batchThreads);
    }

    @Override
    public int launchWithoutDependencies(long executionPlanId, KernelStackFrame frame, XPUBuffer atomicSpace, TaskDataContext meta, long batchThreads) {
        submitWithoutEvents(executionPlanId, (CUDAKernelStackFrame) frame, meta, batchThreads);
        return -1;
    }

    /**
     * Launches after {@code events}: the frame write waits on them and the kernel waits on the frame
     * write, or directly on {@code events} when no write was enqueued.
     *
     * @return the kernel's event
     */
    public int submitWithEvents(long executionPlanId, CUDAKernelStackFrame frame, XPUBuffer atomicSpace, TaskDataContext meta, int[] events, long batchThreads) {
        guarantee(kernel != null, "kernel is null");
        logSubmission();
        setKernelArgs(frame, meta);
        int[] waitEvents = writeFrame(executionPlanId, frame, meta, events);

        if (meta == null) {
            return enqueue(executionPlanId, oneThread, oneThread, waitEvents);
        }
        if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            return scheduler.submit(executionPlanId, kernel, meta, waitEvents, batchThreads);
        }
        if (meta.isDebug()) {
            printDeviceInfo(meta);
        }
        return meta.getGlobalWork() == null //
                ? enqueue(executionPlanId, oneThread, oneThread, waitEvents)
                : enqueue(executionPlanId, meta.getGlobalWork(), meta.getLocalWork(), waitEvents);
    }

    /**
     * Enqueues the frame write after {@code events} and returns what the launch must wait on: the
     * write's event, or {@code events} itself when the write is deferred into a graph capture or the
     * frame was already on the device unchanged (-1, so there is no event to chain onto).
     */
    private int[] writeFrame(long executionPlanId, CUDAKernelStackFrame frame, TaskDataContext meta, int[] events) {
        if (deviceContext.deferKernelContextWriteIfCapturing(executionPlanId, frame)) {
            return events;
        }
        int written = frame.enqueueWrite(executionPlanId, events);
        if (written == -1) {
            return events;
        }
        frameWritten[0] = written;
        recordFrameWrite(executionPlanId, written, meta, frame);
        return frameWritten;
    }

    private void submitWithoutEvents(long executionPlanId, CUDAKernelStackFrame frame, TaskDataContext meta, long batchThreads) {
        if (kernel == null) {
            throw new TornadoRuntimeException("[ERROR] Generated Kernel is NULL. \nPlease report this issue to https://github.com/beehive-lab/TornadoVM");
        }
        logSubmission();
        setKernelArgs(frame, meta);
        if (!deviceContext.deferKernelContextWriteIfCapturing(executionPlanId, frame)) {
            int written = frame.enqueueWrite(executionPlanId);
            if (written != -1) {
                recordFrameWrite(executionPlanId, written, meta, frame);
            }
        }

        if (meta == null) {
            enqueue(executionPlanId, oneThread, oneThread, null);
        } else if (meta.isParallel() || meta.isWorkerGridAvailable()) {
            scheduler.submit(executionPlanId, kernel, meta, batchThreads);
        } else {
            submitSequential(executionPlanId, meta);
        }
    }

    /** A task without a parallel loop or worker grid: one thread, or the ahead-of-time sizes when the task has them. */
    private void submitSequential(long executionPlanId, TaskDataContext meta) {
        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }
        long[] global = meta.getGlobalWork();
        int kernelEvent = global == null || global.length == 0 //
                ? enqueue(executionPlanId, oneThread, oneThread, null)
                : enqueue(executionPlanId, global, meta.getLocalWork(), null);
        // Waiting on an event recorded during a graph capture is illegal and invalidates the capture.
        if (TornadoOptions.isProfilerEnabled() && !deviceContext.isStreamCapturing(executionPlanId)) {
            recordKernelTime(executionPlanId, kernelEvent, meta);
        }
    }

    private int enqueue(long executionPlanId, long[] global, long[] local, int[] waitEvents) {
        return deviceContext.enqueueNDRangeKernel(executionPlanId, kernel, 1, null, global, local, waitEvents);
    }

    private void setKernelArgs(CUDAKernelStackFrame frame, TaskDataContext meta) {
        frame.setDeviceLaunchInfo(meta != null && meta.isThreadInfoEnabled());
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

        // The KernelContext exists only on the Java side: CUDA C kernels read thread ids directly.
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
        if (meta.getXPUDevice() instanceof CUDATornadoDevice device) {
            System.out.println("\tDevice  : " + device.getPhysicalDevice().getDeviceName());
        }
    }

    private void recordFrameWrite(long executionPlanId, int eventId, TaskDataContext meta, CUDAKernelStackFrame frame) {
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
