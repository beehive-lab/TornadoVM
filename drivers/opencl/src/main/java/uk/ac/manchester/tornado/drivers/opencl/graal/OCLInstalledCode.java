/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal;

import static uk.ac.manchester.tornado.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.common.Tornado.debug;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;

import java.nio.ByteBuffer;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLGpuScheduler;
import tornado.drivers.opencl.OCLKernel;
import tornado.drivers.opencl.OCLKernelScheduler;
import tornado.drivers.opencl.OCLProgram;
import tornado.drivers.opencl.OCLScheduler;
import uk.ac.manchester.tornado.api.Event;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.CallStack;
import uk.ac.manchester.tornado.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLCallStack;

public class OCLInstalledCode extends InstalledCode implements TornadoInstalledCode {

    private final OCLKernelScheduler DEFAULT_SCHEDULER;

    //TODO replace with a system property/Tornado setting
    private final ByteBuffer buffer = ByteBuffer.allocate(8);
    private final byte[] code;
    private final OCLDeviceContext deviceContext;
    private final OCLKernel kernel;
    private boolean valid;

    private final OCLKernelScheduler scheduler;
    private final int[] internalEvents = new int[1];

    public OCLInstalledCode(
            final String entryPoint,
            final byte[] code,
            final OCLDeviceContext deviceContext,
            final OCLProgram program,
            final OCLKernel kernel) {
        super(entryPoint);
        this.code = code;
        this.deviceContext = deviceContext;
        this.scheduler = OCLScheduler.create(deviceContext);
        this.DEFAULT_SCHEDULER = new OCLGpuScheduler(deviceContext);
        this.kernel = kernel;
        valid = kernel != null;
        buffer.order(deviceContext.getByteOrder());
    }

    @Override
    public void invalidate() {
        if (valid) {
            kernel.cleanup();
            valid = false;
        }

    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public void execute(final OCLByteBuffer stack, final TaskMetaData meta) {
        debug("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
                kernel.getName(), deviceContext.getDevice().getName());
        debug("\tstack    : buffer id=0x%x, address=0x%x relative=0x%x", stack.toBuffer(),
                stack.toAbsoluteAddress(), stack.toRelativeAddress());

        setKernelArgs(stack, meta);
        stack.write();

        int task;
        if (meta == null) {
            task = deviceContext.enqueueTask(kernel, internalEvents);
            deviceContext.flush();
            deviceContext.finish();
        } else {

            if (meta != null && meta.isParallel()) {
                if (meta.enableThreadCoarsener()) {
                    task = DEFAULT_SCHEDULER.submit(kernel, meta, null);
                } else {
                    task = scheduler.submit(kernel, meta, null);
                }
            } else {
                task = deviceContext.enqueueTask(kernel, null);
            }

            if (meta != null && meta.shouldDumpProfiles()) {
//            meta.addProfile(task);
            }

        }
        //NOTE stack needs to be read so that the return value
        //     is transfered back to the host
        //     - As this is blocking then no clFinish() is needed
        stack.read();

        Event event = deviceContext.resolveEvent(task);

        debug("kernel completed: id=0x%x, method = %s, device = %s",
                kernel.getId(), kernel.getName(),
                deviceContext.getDevice().getName());
        if (event != null) {
            debug("\tstatus   : %s", event.getStatus());

            if (meta != null && meta.enableProfiling()) {
                debug("\texecuting: %f seconds", event.getExecutionTime());
                debug("\ttotal    : %f seconds", event.getTotalTime());
            }
        }
    }

    public void execute(final OCLCallStack stack) {
        execute(stack, null);
        debug("\tdeopt    : 0x%x", stack.getDeoptValue());
        debug("\treturn   : 0x%x", stack.getReturnValue());
    }

    @Override
    public Object executeVarargs(final Object... args) throws InvalidInstalledCodeException {

        // final OCLCallStack callStack = memoryManager.createCallStack(args.length);
        //
        // callStack.reset();
        // callStack.pushArgs(args);
        //
        // execute(callStack);
        // return callStack.getReturnValue();
        return null;
    }

    private String formatArray(final long[] array) {
        final StringBuilder sb = new StringBuilder();

        sb.append("[");
        for (final long value : array) {
            sb.append(" ").append(value);
        }
        sb.append(" ]");

        return sb.toString();
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    private void setKernelArgs(final OCLByteBuffer stack, TaskMetaData meta) {
        int index = 0;

        if (deviceContext.needsBump()) {
            buffer.clear();
            buffer.putLong(deviceContext.getBumpBuffer());
            kernel.setArg(index, buffer);
            index++;
        }

        // heap
        buffer.clear();
        buffer.putLong(stack.toBuffer());
        kernel.setArg(index, buffer);
        index++;

        // stack pointer
        buffer.clear();
        buffer.putLong(stack.toRelativeAddress());
        kernel.setArg(index, buffer);
        index++;

        // constant
        if (meta != null && meta.getConstantSize() > 0) {
            kernel.setArg(index, ByteBuffer.wrap(meta.getConstantData()));
        } else {
            kernel.setArgUnused(index);
        }
        index++;

        // local
        if (meta != null && meta.getLocalSize() > 0) {
            info("\tallocating %s of local memory", humanReadableByteCount(meta.getLocalSize(), true));
            kernel.setLocalRegion(index, meta.getLocalSize());
        } else {
            kernel.setArgUnused(index);
        }
        index++;

        // private
        kernel.setArgUnused(index);
    }

    public int submit(final OCLCallStack stack, final TaskMetaData meta,
            final int[] events) {

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
                    kernel.getName(), deviceContext.getDevice().getName());
            info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(),
                    stack.toAbsoluteAddress(), stack.toRelativeAddress());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have
         * changed
         */
        final int[] waitEvents;
        if (!stack.isOnDevice()) {
            setKernelArgs(stack, meta);
            internalEvents[0] = stack.enqueueWrite(events);
            waitEvents = internalEvents;
        } else {
            waitEvents = events;
        }

        guarantee(kernel != null, "kernel is null");

        int task;
        if (meta == null) {
            task = deviceContext.enqueueTask(kernel, waitEvents);
        } else {
            if (meta.isParallel()) {
                if (meta.enableThreadCoarsener()) {
                    //FIXME hack to support both the old paralleliser and the
                    //      new thread coarsener schemes
                    task = DEFAULT_SCHEDULER.submit(kernel, meta, waitEvents);
                } else {
                    task = scheduler.submit(kernel, meta, waitEvents);
                }
            } else {
                task = deviceContext.enqueueTask(kernel, waitEvents);
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

    public void submit(final OCLCallStack stack, final TaskMetaData meta) {

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
                    kernel.getName(), deviceContext.getDevice().getName());
            info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(),
                    stack.toAbsoluteAddress(), stack.toRelativeAddress());
        }

        /*
         * Only set the kernel arguments if they are either: - not set or - have
         * changed
         */
        if (!stack.isOnDevice()) {
            setKernelArgs(stack, meta);
            stack.enqueueWrite();
        }

        guarantee(kernel != null, "kernel is null");

        if (meta == null) {
            deviceContext.enqueueTask(kernel, null);
        } else {

            final int task;
            if (meta.isParallel()) {
                //FIXME hack to support both the old paralleliser and the
                //      new thread coarsener schemes
                if (meta.enableThreadCoarsener()) {
                    task = DEFAULT_SCHEDULER.submit(kernel, meta, null);
                } else {
                    task = scheduler.submit(kernel, meta, null);
                }
            } else {
                task = deviceContext.enqueueTask(kernel, null);
            }

            if (meta.shouldDumpProfiles()) {
                deviceContext.retainEvent(task);
                meta.addProfile(task);
            }

            if (meta.enableExceptions()) {
                stack.enqueueRead(null);
            }
        }
    }

    @Override
    public int launchWithDeps(CallStack stack, TaskMetaData meta, int[] waitEvents) {
        return submit((OCLCallStack) stack, meta, waitEvents);
    }

    @Override
    public int launchWithoutDeps(CallStack stack, TaskMetaData meta) {
        submit((OCLCallStack) stack, meta);
        return -1;
    }

}
