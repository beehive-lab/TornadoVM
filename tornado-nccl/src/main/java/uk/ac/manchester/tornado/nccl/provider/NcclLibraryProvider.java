/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.nccl.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.nccl.Nccl;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for NVIDIA NCCL collectives. A task refers to its communicator by
 * handle; the provider picks the rank of the communicator that lives on the task's device and
 * enqueues the collective on the plan's stream, with the TornadoVM buffers as send and receive
 * buffers.
 *
 * <p>
 * The per-(device, plan) context only records which CUDA device the TornadoVM device is: the
 * communicator is created by the application across several devices, so it cannot be owned by a
 * context that belongs to one of them. NCCL allocates nothing per call, so there is no
 * {@code prepare} step.
 * </p>
 */
public final class NcclLibraryProvider implements TornadoLibraryProvider {

    private record NcclContext(int ordinal) implements LibraryContext {
    }

    /** Whether NCCL can be reached on this host. */
    public static boolean isAvailable() {
        return NcclNativeLib.isAvailable();
    }

    @Override
    public String libraryName() {
        return Nccl.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;
    }

    /** Called with the device's context current, so the CUDA runtime reports this device's ordinal. */
    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        NcclNativeLib.load();
        return new NcclContext(NcclNativeLib.currentDeviceOrdinal());
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        NcclContext context = (NcclContext) invocation.getContext();
        long comm = NcclCommunicator.fromHandle((long) invocation.getArg(0)).commForOrdinal(context.ordinal());
        // Looked up on every call rather than cached: the stream belongs to the thread that runs the plan.
        long stream = ((TornadoNativeStreamSupport) invocation.getDevice()).getNativeStream(invocation.getExecutionPlanId());

        switch (functionName) {
            case "allReduce" -> NcclNativeLib.allReduce(invocation.getDevicePointer(1), invocation.getDevicePointer(2), (long) invocation.getArg(5), (int) invocation.getArg(4),
                    (int) invocation.getArg(3), comm, stream);
            case "allReduceInPlace" -> {
                long buffer = invocation.getDevicePointer(1);
                NcclNativeLib.allReduce(buffer, buffer, (long) invocation.getArg(4), (int) invocation.getArg(3), (int) invocation.getArg(2), comm, stream);
            }
            case "broadcast" -> {
                long buffer = invocation.getDevicePointer(1);
                NcclNativeLib.broadcast(buffer, buffer, (long) invocation.getArg(4), (int) invocation.getArg(3), (int) invocation.getArg(2), comm, stream);
            }
            case "reduce" -> NcclNativeLib.reduce(invocation.getDevicePointer(1), invocation.getDevicePointer(2), (long) invocation.getArg(6), (int) invocation.getArg(5), (int) invocation.getArg(3),
                    (int) invocation.getArg(4), comm, stream);
            case "allGather" -> NcclNativeLib.allGather(invocation.getDevicePointer(1), invocation.getDevicePointer(2), (long) invocation.getArg(4), (int) invocation.getArg(3), comm, stream);
            case "reduceScatter" -> NcclNativeLib.reduceScatter(invocation.getDevicePointer(1), invocation.getDevicePointer(2), (long) invocation.getArg(5), (int) invocation.getArg(4),
                    (int) invocation.getArg(3), comm, stream);
            default -> throw new TornadoRuntimeException("[ERROR] NCCL function not supported: " + functionName);
        }
    }

    @Override
    public void destroyContext(LibraryContext context) {
        // Communicators belong to the application and are closed by it.
    }
}
