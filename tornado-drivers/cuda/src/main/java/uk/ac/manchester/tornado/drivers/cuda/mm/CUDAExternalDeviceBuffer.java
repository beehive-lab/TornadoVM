/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.DeviceViewBinding;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;

/**
 * The device buffer of a {@code deviceView} array: a CUDA address somebody else allocated, which
 * TornadoVM borrows for the length of a kernel and gives back untouched.
 *
 * <p>
 * Every operation that would move or release memory is deliberately a no-op here. The view is not
 * allocated (the memory exists already), not written to from the host and not read back to it
 * (there is no host segment to copy to or from), and not freed on release or on
 * {@code freeDeviceMemory()} -- freeing a cuDF or RMM allocation from under its owner would be a
 * use-after-free in that library. The address itself is read from the array's
 * {@link DeviceViewBinding} on every use, so rebinding the view between executions -- a new batch,
 * a different pointer, a different row count -- takes effect without reallocating anything.
 * </p>
 *
 * <p>
 * The kernel reaches element {@code i} at {@code toBuffer() + i * elementSize}: no native-array
 * header is written into or read from the foreign buffer, which is why the argument has to be
 * compiled headerless (see {@code ValueSpecialisation.Fact.DeviceView}).
 * </p>
 */
public class CUDAExternalDeviceBuffer implements XPUBuffer {

    private final CUDADeviceContext deviceContext;
    private final TornadoNativeArray view;
    private final DeviceViewBinding binding;
    private final Access access;
    private final int sizeOfType;

    private long subregionSize;

    public CUDAExternalDeviceBuffer(CUDADeviceContext deviceContext, TornadoNativeArray view, Access access) {
        this.deviceContext = deviceContext;
        this.view = view;
        this.binding = view.deviceViewBinding();
        this.access = access;
        this.sizeOfType = binding.elementSize();
    }

    @Override
    public long toBuffer() {
        return binding.requireBoundBuffer() + binding.byteOffset();
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        throw new TornadoRuntimeException("A device view's address comes from its binding and cannot be set from a buffer wrapper");
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    /** The data starts at the address itself: a borrowed buffer has no header to skip. */
    @Override
    public long libraryAddress() {
        return toBuffer();
    }

    @Override
    public long libraryOffset() {
        return 0;
    }

    @Override
    public void read(long executionPlanId, Object reference) {
        // Nothing to read back: the data is already where its owner wants it, and there is no host segment.
    }

    @Override
    public int read(long executionPlanId, Object reference, long hostOffset, long partialReadSize, int[] events, boolean useDeps) {
        return -1;
    }

    @Override
    public void write(long executionPlanId, Object reference) {
        // Nothing to upload: the owner put the data there.
    }

    @Override
    public int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps) {
        return -1;
    }

    @Override
    public List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps) {
        return List.of();
    }

    @Override
    public void allocate(Object reference, long batchSize, Access requestedAccess) {
        if (batchSize > 0) {
            throw new TornadoRuntimeException("A device view cannot be used in a batched task-graph: its device memory is a fixed allocation somebody else owns, "
                    + "so TornadoVM cannot split it into chunks. Bind one view per batch instead.");
        }
        validateForAccess(requestedAccess);
    }

    /**
     * Checks the binding against what the task will do to it.
     *
     * <p>
     * This has to run on every execution rather than once when the wrapper is created. A plan that
     * reuses a locked buffer never allocates again, so a view bound read-write for one batch and
     * rebound read-only for the next was still written by the task that had already been compiled
     * against it: the protection existed but the reuse path walked past it.
     * </p>
     */
    public void validateForAccess(Access requestedAccess) {
        validateBinding(binding, deviceContext.getDeviceIndex(), requestedAccess);
    }

    /**
     * Checks a binding against the device about to use it and what the task will do to it, and only
     * then treats its handle as a device address.
     */
    public static void validateBinding(DeviceViewBinding binding, int deviceIndex, Access access) {
        binding.validateForLaunch(TornadoVMBackendType.CUDA, deviceIndex, access);
        // A device address is unsigned, so wrapping past the top of the address space is checked as such.
        long base = binding.deviceBuffer();
        long address = base + binding.byteOffset();
        long end = address + binding.deviceByteLength();
        if (Long.compareUnsigned(address, base) < 0 || Long.compareUnsigned(end, address) < 0) {
            throw new TornadoRuntimeException("The device view's range at 0x" + Long.toHexString(base) + " wraps around the address space");
        }
        if (Long.remainderUnsigned(address, binding.elementSize()) != 0) {
            throw new TornadoRuntimeException("The device address 0x" + Long.toHexString(address) + " is not aligned to the " + binding.elementSize() + "-byte element size");
        }
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        // Borrowed: releasing it here would free memory that belongs to the library that allocated it.
    }

    @Override
    public long deallocate() {
        return 0;
    }

    @Override
    public long size() {
        return binding.deviceByteLength();
    }

    /** Borrowed: none of these bytes are the runtime's. */
    @Override
    public long getAllocatedSize() {
        return 0;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.subregionSize = batchSize;
    }

    @Override
    public long getSizeSubRegionSize() {
        return subregionSize;
    }

    @Override
    public void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset) {
        throw new TornadoRuntimeException("A device view cannot be mapped onto another buffer's region: its memory is not TornadoVM's to place");
    }

    @Override
    public int getSizeOfType() {
        return sizeOfType;
    }

    public TornadoNativeArray getView() {
        return view;
    }

    public CUDADeviceContext getDeviceContext() {
        return deviceContext;
    }

    @Override
    public String toString() {
        return "CUDAExternalDeviceBuffer[" + access + ", " + binding + "]";
    }
}
