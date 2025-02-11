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
package uk.ac.manchester.tornado.drivers.spirv.mm;

import java.lang.reflect.Array;
import java.util.function.Function;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class SPIRVMultiDimArrayWrapper<T, E> extends SPIRVArrayWrapper<T> {

    private final Function<SPIRVDeviceContext, ? extends SPIRVArrayWrapper<E>> innerWrapperFactory;
    private final SPIRVLongArrayWrapper tableWrapper;
    private long[] addresses;
    private SPIRVArrayWrapper<E>[] wrappers;
    private final SPIRVDeviceContext deviceContext;
    private Access access;

    public SPIRVMultiDimArrayWrapper(SPIRVDeviceContext deviceContext, Function<SPIRVDeviceContext, ? extends SPIRVArrayWrapper<E>> innerWrapperFactory, long batchSize, Access access) {
        super(deviceContext, JavaKind.Object, batchSize, access);
        this.deviceContext = deviceContext;
        this.innerWrapperFactory = innerWrapperFactory;
        this.tableWrapper = new SPIRVLongArrayWrapper(deviceContext, batchSize, access);
        this.access = access;
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
    public int getSizeOfType() {
        throw new TornadoRuntimeException("[ERROR] OCLMultiDimArrayWrapper getSizeOfType not supported");
    }

    private E[] innerCast(T value) {
        return (E[]) value;
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
            throw new RuntimeException(e);
        }
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
        wrappers = new SPIRVArrayWrapper[Array.getLength(value)];
        tableWrapper.allocate(addresses, batchSize, access);
        allocateElements((T) value, batchSize, access);
    }

    private int readElements(long executionPlanId, T values) {
        final E[] elements = innerCast(values);
        // XXX: Offset is 0
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].read(executionPlanId, elements[i], 0, 0, null, false);
        }
        deviceContext.enqueueBarrier(executionPlanId, deviceContext.getDeviceIndex());
        return 0;
    }

    @Override
    protected int readArrayData(long executionPlanId, long bufferId, long offset, long bytes, T value, long hostOffset, int[] waitEvents) {
        return readElements(executionPlanId, value);
    }

    private int writeElements(long executionPlanId, T values) {
        final E[] elements = innerCast(values);
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].enqueueWrite(executionPlanId, elements[i], 0, 0, null, false);
        }
        deviceContext.enqueueBarrier(executionPlanId, deviceContext.getDeviceIndex());
        return 0;
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
}
