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
package uk.ac.manchester.tornado.drivers.metal.mm;

import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.memory.DeviceViewBinding;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.metal.ffm.MetalAPI;
import uk.ac.manchester.tornado.drivers.metal.ffm.MetalObjects;

/**
 * The device buffer of a {@code deviceView} array: an {@code MTLBuffer} somebody else allocated, which
 * TornadoVM borrows for the length of a kernel and gives back untouched.
 *
 * <p>
 * Every operation that would move or release memory is deliberately a no-op here. The view is not
 * allocated (the memory exists already), not written to from the host and not read back to it
 * (there is no host segment to copy to or from), and not released on
 * {@code freeDeviceMemory()}. The buffer and its offset are read from the array's
 * {@link DeviceViewBinding} on every launch, so rebinding the view between executions takes effect
 * without reallocating anything.
 * </p>
 *
 * <p>
 * The kernel reaches element {@code i} at {@code i * elementSize} bytes from where the buffer is bound:
 * no native-array header is written into or read from it, which is why the argument has to be
 * compiled headerless (see {@code ValueSpecialisation.Fact.DeviceView}).
 * </p>
 *
 * <p>
 * The buffer's storage mode is the owner's business. With managed storage, keeping the CPU's and the
 * GPU's copies of the contents coherent is the owner's too: TornadoVM does not synchronise them.
 * </p>
 */
public class MetalExternalDeviceBuffer implements XPUBuffer {

    /**
     * What a kernel launch binds for a borrowed buffer. The offset belongs to this argument and not to
     * the handle: two views can bind one {@code MTLBuffer} at different offsets in one kernel.
     */
    public record BorrowedBufferArgument(long buffer, long byteOffset) {
    }

    /** -Dtornado.mlx.async=true: library calls only encode GPU work and never read buffers on the CPU. */
    private static final boolean ASYNC_LIBRARY_DISPATCH = Boolean.getBoolean("tornado.mlx.async");

    private final MetalDeviceContextInterface deviceContext;
    private final TornadoNativeArray view;
    private final DeviceViewBinding binding;
    private final Access access;
    private final int sizeOfType;

    private long subregionSize;

    public MetalExternalDeviceBuffer(MetalDeviceContextInterface deviceContext, TornadoNativeArray view, Access access) {
        this.deviceContext = deviceContext;
        this.view = view;
        this.binding = view.deviceViewBinding();
        this.access = access;
        this.sizeOfType = binding.elementSize();
    }

    /**
     * Checks a binding against the device about to use it and what the task will do to it. The handle
     * is only sent a message once {@link DeviceViewBinding#validateForLaunch} has established that it
     * is an {@code MTLBuffer} handle; whether it is a live one is the caller's contract.
     */
    public static void validateBinding(DeviceViewBinding binding, MetalDeviceContextInterface deviceContext, Access access) {
        binding.validateForLaunch(TornadoVMBackendType.METAL, deviceContext.getDeviceIndex(), access);
        long buffer = binding.deviceBuffer();
        if (MetalAPI.bufferDevice(buffer) != deviceContext.getDeviceId()) {
            throw new TornadoRuntimeException("This device view is bound to an MTLBuffer of another device than the one this task runs on");
        }
        // DeviceViewBinding.bind has checked that the sum does not overflow.
        long end = binding.byteOffset() + binding.deviceByteLength();
        long length = MetalAPI.bufferLength(buffer);
        if (end > length) {
            throw new TornadoRuntimeException("This device view is bound up to byte " + end + " of an MTLBuffer of " + length + " bytes: its range lies past the end of the buffer");
        }
    }

    @Override
    public long toBuffer() {
        return binding.requireBoundBuffer();
    }

    @Override
    public Object kernelArgument() {
        return new BorrowedBufferArgument(binding.requireBoundBuffer(), binding.byteOffset());
    }

    @Override
    public void setBuffer(XPUBufferWrapper bufferWrapper) {
        throw new TornadoRuntimeException("A device view's buffer comes from its binding and cannot be set from a buffer wrapper");
    }

    @Override
    public long getBufferOffset() {
        return binding.byteOffset();
    }

    /**
     * CPU address of the first element, for a library task. The CPU may not use a buffer with private
     * storage, whatever {@code contents} answers for it, and the interpreter asks for the address of
     * every argument of a library task, so a library task cannot take one.
     */
    @Override
    public long libraryAddress() {
        long buffer = toBuffer();
        if (MetalAPI.bufferStorageMode(buffer) == MetalAPI.MTL_STORAGE_MODE_PRIVATE) {
            throw new TornadoRuntimeException("A library task cannot use this device view: its MTLBuffer has private storage, which the CPU may not use. Bind a buffer with shared storage.");
        }
        long contents = MetalAPI.bufferContents(buffer);
        if (contents == 0) {
            throw new TornadoRuntimeException("A library task cannot use this device view: its MTLBuffer reports no CPU address");
        }
        // A library may read this address on the CPU: pending kernels that bind the buffer must finish first.
        // A library that only encodes GPU work on the queue (-Dtornado.mlx.async=true) never reads it.
        if (!ASYNC_LIBRARY_DISPATCH) {
            MetalObjects.awaitBuffer(buffer);
        }
        return contents + binding.byteOffset();
    }

    /** The data starts at the bound offset: a borrowed buffer has no header to skip. */
    @Override
    public long libraryOffset() {
        return binding.byteOffset();
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
     * Checks the binding for a caller that reaches this buffer through {@code allocate}. It has to run
     * when a locked buffer is reused as well, because the binding can have changed since.
     */
    public void validateForAccess(Access requestedAccess) {
        validateBinding(binding, deviceContext, requestedAccess);
    }

    @Override
    public void markAsFreeBuffer() throws TornadoMemoryException {
        // Borrowed: the buffer belongs to the library that allocated it.
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

    @Override
    public String toString() {
        return "MetalExternalDeviceBuffer[" + access + ", " + binding + "]";
    }
}
