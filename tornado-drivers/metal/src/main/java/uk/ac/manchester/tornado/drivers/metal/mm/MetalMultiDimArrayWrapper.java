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
package uk.ac.manchester.tornado.drivers.metal.mm;

import java.lang.reflect.Array;
import java.util.function.Function;

import uk.ac.manchester.tornado.runtime.common.JavaKind;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class MetalMultiDimArrayWrapper<T, E> extends MetalArrayWrapper<T> {

    private final Function<MetalDeviceContext, ? extends MetalArrayWrapper<E>> innerWrapperFactory;
    private final MetalLongArrayWrapper tableWrapper;
    private long[] addresses;
    private MetalArrayWrapper<E>[] wrappers;
    private final MetalDeviceContext deviceContext;
    private Access access;

    public MetalMultiDimArrayWrapper(MetalDeviceContext device, Function<MetalDeviceContext, ? extends MetalArrayWrapper<E>> factory, long batchSize, Access access) {
        super(device, JavaKind.Object, batchSize, access);
        this.deviceContext = device;
        this.access = access;
        innerWrapperFactory = factory;
        tableWrapper = new MetalLongArrayWrapper(device, batchSize, access);
    }

    @Override
    public long toBuffer() {
        return tableWrapper.toBuffer();
    }

    @Override
    public long size() {
        return tableWrapper.size();
    }

    @Override
    public void allocate(Object value, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {

        if (batchSize > 0) {
            throw new TornadoMemoryException("[ERROR] BatchSize Allocation currently not supported. BatchSize = " + batchSize + " (bytes)");
        }

        if (Array.getLength(value) < 0) {
            throw new TornadoMemoryException("[ERROR] Bytes Allocated < 0: " + Array.getLength(value));
        }
        addresses = new long[Array.getLength(value)];
        wrappers = new MetalArrayWrapper[Array.getLength(value)];
        tableWrapper.allocate(addresses, batchSize, access);
        allocateElements((T) value, batchSize, access);
    }

    private void allocateElements(T values, long batchSize, Access access) {
        final E[] elements = innerCast(values);
        try {
            for (int i = 0; i < elements.length; i++) {
                wrappers[i] = innerWrapperFactory.apply(deviceContext);
                wrappers[i].allocate(elements[i], batchSize, access);
                addresses[i] = wrappers[i].toBuffer();
            }
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            new TornadoLogger().fatal("OOM: multi-dim array: %s", e.getMessage());
            System.exit(-1);
        }
    }

    private int writeElements(long executionPlanId, T values) {
        final E[] elements = innerCast(values);
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].enqueueWrite(executionPlanId, elements[i], 0, 0, null, false);
        }
        return deviceContext.enqueueBarrier(executionPlanId);
    }

    private int readElements(long executionPlanId, T values) {
        final E[] elements = innerCast(values);
        // XXX: Offset is 0
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].enqueueRead(executionPlanId, elements[i], 0, null, false);
        }
        return deviceContext.enqueueBarrier(executionPlanId);
    }

    @SuppressWarnings("unchecked")
    private E[] innerCast(T value) {
        return (E[]) value;
    }

    @Override
    protected int enqueueReadArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents) {
        return readElements(executionPlanId, value);
    }

    @Override
    protected int enqueueWriteArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents) {
        if (hostOffset > 0) {
            System.out.println("[WARNING] writing in offset 0");
        }
        tableWrapper.enqueueWrite(executionPlanId, addresses, 0, 0, null, false);
        return writeElements(executionPlanId, value);
    }

    @Override
    protected int readArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents) {
        return readElements(executionPlanId, value);
    }

    @Override
    protected void writeArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents) {
        if (hostOffset > 0) {
            System.out.println("[WARNING] writing in offset 0");
        }
        tableWrapper.enqueueWrite(executionPlanId, addresses, 0, 0, null, false);
        writeElements(executionPlanId, value);
    }

    @Override
    public int getSizeOfType() {
        throw new TornadoRuntimeException("[ERROR] MetalMultiDimArrayWrapper getSizeOfType not supported");
    }

}
