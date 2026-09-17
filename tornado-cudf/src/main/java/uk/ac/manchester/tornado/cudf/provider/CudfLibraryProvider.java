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
package uk.ac.manchester.tornado.cudf.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.cudf.Cudf;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * {@link TornadoLibraryProvider} for RAPIDS cuDF: sort, grouped aggregation, running sum and inner
 * join, each running on the task graph's own stream.
 *
 * <p>
 * There is no handle to create and no workspace to size, which is the difference from cuSPARSE:
 * cuDF allocates through RMM per call and every buffer this module touches belongs to TornadoVM
 * already. The context therefore holds only the stream, and exists so that a task graph executed
 * twice concurrently does not share one.
 *
 * <p>
 * {@link #canHandle} answers false when the shim is missing, which is the usual case. A provider
 * that declines is not an error -- the caller falls back the same way it would on a machine with
 * no device at all.
 */
public final class CudfLibraryProvider implements TornadoLibraryProvider {

    private static final class CudfContext implements LibraryContext {
        private final long stream;

        private CudfContext(long stream) {
            this.stream = stream;
        }
    }

    /**
     * Whether the shim is present on this host, for callers that want to ask before building a
     * task graph around it. Usually false, and that is not a fault -- see the module README.
     */
    public static boolean isAvailable() {
        return CudfNativeLib.isAvailable();
    }

    @Override
    public String libraryName() {
        return Cudf.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport && CudfNativeLib.isAvailable();
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        CudfNativeLib.load();
        return new CudfContext(((TornadoNativeStreamSupport) device).getNativeStream(executionPlanId));
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        CudfContext context = (CudfContext) invocation.getContext();
        long stream = context.stream;
        int status = switch (functionName) {
            case "sortPairs" -> CudfNativeLib.sortPairs(stream, invocation.getDevicePointer(1), invocation.getDevicePointer(2), (Integer) invocation.getArg(0), invocation.getDevicePointer(3),
                    invocation.getDevicePointer(4));
            case "sortedOrder" -> CudfNativeLib.sortedOrder(stream, invocation.getDevicePointer(1), (Integer) invocation.getArg(0), (Integer) invocation.getArg(2),
                    invocation.getDevicePointer(3));
            case "groupSum" -> CudfNativeLib.groupSum(stream, invocation.getDevicePointer(1), invocation.getDevicePointer(2), (Integer) invocation.getArg(0), invocation.getDevicePointer(3),
                    invocation.getDevicePointer(4), invocation.getDevicePointer(5));
            case "runningSum" -> CudfNativeLib.runningSum(stream, invocation.getDevicePointer(1), (Integer) invocation.getArg(0), invocation.getDevicePointer(2));
            case "innerJoin" -> CudfNativeLib.innerJoin(stream, invocation.getDevicePointer(1), (Integer) invocation.getArg(0), invocation.getDevicePointer(3), (Integer) invocation.getArg(2),
                    (Integer) invocation.getArg(4), invocation.getDevicePointer(5), invocation.getDevicePointer(6), invocation.getDevicePointer(7));
            default -> throw new TornadoRuntimeException("[ERROR] cuDF function not supported: " + functionName);
        };
        CudfNativeLib.checkStatus(status, functionName);
    }

    @Override
    public void destroyContext(LibraryContext context) {
        // Nothing to release: the stream belongs to the execution plan and cuDF's own allocations
        // are freed inside the shim before it returns.
    }
}
