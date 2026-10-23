/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class CUDAAtomicsBuffer implements XPUBuffer {

    private int[] atomicsList;
    private static final int OFFSET = 0;
    private final CUDADeviceContext deviceContext;
    private long setSubRegionSize;
    private Access access;

    private static final TornadoLogger logger = new TornadoLogger(CUDAAtomicsBuffer.class);

    public CUDAAtomicsBuffer(int[] arr, CUDADeviceContext deviceContext, Access access) {
        this.deviceContext = deviceContext;
        this.atomicsList = arr;
        this.access = access;
        deviceContext.getMemoryManager().allocateAtomicRegion();
    }

    @Override
    public long toBuffer() {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void read(long executionPlanId, Object reference) {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public int read(long executionPlanId, Object reference, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public void write(long executionPlanId, Object reference) {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps) {
        return deviceContext.readBuffer(executionPlanId, deviceContext.getMemoryManager().toAtomicAddress(), OFFSET, 4 * atomicsList.length, atomicsList, 0, events);
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        // Non-blocking write
        if (atomicsList.length == 0) {
            return null;
        }
        return new ArrayList<>(deviceContext.enqueueWriteBuffer(executionPlanId, deviceContext.getMemoryManager().toAtomicAddress(), OFFSET, 4 * atomicsList.length, atomicsList, 0, events));
    }

    @Override
    public void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException {
        deviceContext.getMemoryManager().allocateAtomicRegion();
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        logger.debug("Marking atomics buffer as free has no effect because we do not use the BufferProvider for this buffer.");
    }

    @Override
    public long size() {
        return atomicsList.length * 4;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public int[] getIntBuffer() {
        return atomicsList;
    }

    @Override
    public void setIntBuffer(int[] arr) {
        this.atomicsList = arr;
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public int getSizeOfType() {
        throw new TornadoRuntimeException("[ERROR] not implemented");
    }

    @Override
    public long deallocate() {
        // Do not deallocate the global area for atomics by default since all
        // atomics go to the same area
        if (TornadoOptions.cleanUpAtomicsSpace()) {
            deviceContext.getMemoryManager().deallocateAtomicRegion();
            return CUDAMemoryManager.atomicRegionSize();
        }
        return 0;
    }

}
