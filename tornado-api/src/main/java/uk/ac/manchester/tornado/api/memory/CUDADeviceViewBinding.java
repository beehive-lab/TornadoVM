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
package uk.ac.manchester.tornado.api.memory;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceViewNotSupported;

/**
 * The binding of a view created by a {@code cudaDeviceView} factory: a compatibility form of
 * {@link DeviceViewBinding} for code written against the CUDA-only API. Its handle is always a CUDA
 * device address, so it cannot be bound for another backend; a view for Metal comes from a
 * {@code deviceView} factory.
 */
public final class CUDADeviceViewBinding extends DeviceViewBinding {

    public CUDADeviceViewBinding(long capacityElements, int elementSize) {
        super(capacityElements, elementSize);
    }

    /** The bound device address of the first element, or 0 while unbound. */
    public long devicePointer() {
        return isBound() ? deviceBuffer() + byteOffset() : 0;
    }

    /**
     * Binds a borrowed device address to this view, replacing whatever it was bound to.
     *
     * @param address
     *         the device address of the first element; must not be 0
     * @param byteLength
     *         how many bytes are readable (and writable, for a read-write view) from {@code address}
     * @param elements
     *         how many elements of that span hold data; -1 derives it from {@code byteLength}
     * @param targetDeviceIndex
     *         the index of the device the address belongs to, or -1 when not known
     * @param readOnlyBinding
     *         whether the kernel may only read through this view
     * @param holder
     *         the token that owns the binding until {@link #unbind} is called with it; {@code null}
     *         binds without an owner
     */
    public void bind(long address, long byteLength, long elements, int targetDeviceIndex, boolean readOnlyBinding, Object holder) {
        if (address % elementSize() != 0) {
            throw new IllegalArgumentException("The device address 0x" + Long.toHexString(address) + " is not aligned to the " + elementSize() + "-byte element size");
        }
        bind(TornadoVMBackendType.CUDA, address, 0, byteLength, elements, targetDeviceIndex, readOnlyBinding, holder);
    }

    /**
     * {@inheritDoc}
     *
     * @throws TornadoDeviceViewNotSupported
     *         if {@code handleBackend} is not CUDA
     */
    @Override
    public void bind(TornadoVMBackendType handleBackend, long buffer, long offset, long byteLength, long elements, int targetDeviceIndex, boolean readOnlyBinding, Object holder) {
        if (handleBackend != TornadoVMBackendType.CUDA) {
            throw new TornadoDeviceViewNotSupported("A view created by a cudaDeviceView factory holds a CUDA device address, and cannot be bound on " + handleBackend
                    + ". Create the view with a deviceView factory instead.");
        }
        super.bind(handleBackend, buffer, offset, byteLength, elements, targetDeviceIndex, readOnlyBinding, holder);
    }

    /** The bound address of the first element, failing with a clear message when the view was never bound. */
    public long requireBoundPointer() {
        return requireBoundBuffer() + byteOffset();
    }
}
