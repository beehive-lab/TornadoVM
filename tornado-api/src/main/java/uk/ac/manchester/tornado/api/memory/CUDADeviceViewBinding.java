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

import java.util.concurrent.atomic.AtomicReference;

/**
 * The device-only state of a {@code cudaDeviceView} native array: an array that has no host
 * {@code MemorySegment} and whose storage is a device allocation somebody else owns.
 *
 * <p>
 * A view is created detached ({@link #isBound()} is false) and is bound to a device address before
 * a plan that uses it executes, typically once per batch. The address is <b>borrowed</b>: TornadoVM
 * never frees it, never writes a native-array header into it and never transfers it implicitly, so
 * the data a foreign library (cuDF/RMM, for instance) placed there is what the kernel reads.
 * </p>
 *
 * <p>
 * Lengths. {@code capacityElements} is how many elements the view can address at most; it is fixed
 * at creation and is what plan construction sizes against. {@code logicalLength} is how many
 * elements the current binding actually holds, which may change on every rebind; it is what
 * {@code getSize()} reports on the host. Neither is the number of rows of the caller's batch: a
 * {@code LIST} offsets array holds {@code numRows + 1} elements and a flattened child holds as many
 * as the rows together need.
 * </p>
 *
 * <p>
 * A kernel cannot read a length out of a headerless allocation, so {@code getSize()} works inside
 * one only when {@link #isLengthVisibleToKernel()} is on. It is off by default.
 * </p>
 *
 * <p>
 * Ownership. {@link #bind} fails when another holder already owns the binding, so two threads
 * cannot rebind one view underneath each other; a view belongs to one task at a time.
 * </p>
 */
public final class CUDADeviceViewBinding {

    /** No owner: the token {@link #bind} uses when the caller passes {@code null}. */
    private static final Object NO_OWNER = new Object();

    private final long capacityElements;
    private final int elementSize;

    private volatile long logicalLength;
    private volatile long devicePointer;
    private volatile long deviceByteLength;
    private volatile int deviceIndex = -1;
    private volatile boolean readOnly;
    private volatile boolean lengthVisibleToKernel;

    private final AtomicReference<Object> owner = new AtomicReference<>();

    /**
     * The logical length that has been folded into compiled code, once that has happened. Rebinding
     * to a different length does not recompile, so the installed constant would outlive the binding
     * it described; {@link #isLengthVisibleToKernel()} says what is done about that.
     */
    private volatile long lengthFoldedIntoKernel = -1;

    public CUDADeviceViewBinding(long capacityElements, int elementSize) {
        if (capacityElements < 0) {
            throw new IllegalArgumentException("A device view cannot have a negative capacity: " + capacityElements);
        }
        if (elementSize <= 0) {
            throw new IllegalArgumentException("Invalid element size: " + elementSize);
        }
        this.capacityElements = capacityElements;
        this.elementSize = elementSize;
        this.logicalLength = capacityElements;
    }

    public long capacityElements() {
        return capacityElements;
    }

    public int elementSize() {
        return elementSize;
    }

    /** Bytes the view can address at most: {@code capacityElements * elementSize}. */
    public long capacityBytes() {
        return capacityElements * elementSize;
    }

    public long logicalLength() {
        return logicalLength;
    }

    /**
     * How many elements the current binding holds; at most the capacity, and at most what is
     * actually bound. Checking only the capacity let a view claim more elements than the memory
     * behind it holds, which is a read past the end of somebody else's allocation.
     */
    public void setLogicalLength(long elements) {
        if (elements < 0 || elements > capacityElements) {
            throw new IllegalArgumentException("Logical length " + elements + " is outside the view's capacity of " + capacityElements + " elements");
        }
        if (devicePointer != 0 && elements > deviceByteLength / elementSize) {
            throw new IllegalArgumentException("Logical length " + elements + " is more than the " + (deviceByteLength / elementSize)
                    + " elements currently bound (" + deviceByteLength + " bytes); the extra elements are not memory this view was given");
        }
        if (lengthVisibleToKernel && lengthFoldedIntoKernel >= 0 && elements != lengthFoldedIntoKernel) {
            throw new IllegalStateException("This view's length is visible to the kernel and " + lengthFoldedIntoKernel
                    + " has already been compiled into it, so it cannot now become " + elements
                    + ". Leave setLengthVisibleToKernel off and pass the length to the kernel as a parameter instead.");
        }
        this.logicalLength = elements;
    }

    public boolean isLengthVisibleToKernel() {
        return lengthVisibleToKernel;
    }

    /**
     * Whether a kernel may call {@code getSize()} on this view.
     *
     * <p>
     * Turning this on fixes the view's logical length: it is compiled into the kernel as a
     * constant, and a later rebind to a different length is refused rather than silently running
     * code specialised for the old one. A view that genuinely changes length between batches should
     * leave this off and have the kernel take the length as an ordinary parameter, which is what
     * the length is for.
     * </p>
     */
    public void setLengthVisibleToKernel(boolean visible) {
        if (!visible) {
            this.lengthVisibleToKernel = false;
            this.lengthFoldedIntoKernel = -1;
            return;
        }
        this.lengthVisibleToKernel = true;
        this.lengthFoldedIntoKernel = logicalLength;
    }

    /**
     * Records that compiled code now contains this view's logical length, so that a later rebind to
     * a different length can be refused instead of running against the stale constant.
     */
    public void noteLengthFoldedIntoKernel() {
        if (lengthVisibleToKernel) {
            this.lengthFoldedIntoKernel = logicalLength;
        }
    }

    public boolean isBound() {
        return devicePointer != 0;
    }

    public long devicePointer() {
        return devicePointer;
    }

    public long deviceByteLength() {
        return deviceByteLength;
    }

    /** The device the address belongs to, or -1 when the caller did not say. */
    public int deviceIndex() {
        return deviceIndex;
    }

    public boolean isReadOnly() {
        return readOnly;
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
        if (address == 0) {
            throw new IllegalArgumentException("A device view cannot be bound to the null device address");
        }
        if (byteLength <= 0) {
            throw new IllegalArgumentException("A device view needs a positive byte length, not " + byteLength);
        }
        if (byteLength > capacityBytes()) {
            throw new IllegalArgumentException("The binding is " + byteLength + " bytes, more than the view's capacity of " + capacityBytes() + " bytes");
        }
        if (byteLength % elementSize != 0) {
            throw new IllegalArgumentException("The binding is " + byteLength + " bytes, which is not a multiple of the " + elementSize + "-byte element size");
        }
        if (address % elementSize != 0) {
            throw new IllegalArgumentException("The device address 0x" + Long.toHexString(address) + " is not aligned to the " + elementSize + "-byte element size");
        }
        long bound = elements < 0 ? byteLength / elementSize : elements;
        if (bound > byteLength / elementSize) {
            throw new IllegalArgumentException("Logical length " + bound + " does not fit in the " + byteLength + " bytes bound");
        }
        if (lengthVisibleToKernel && lengthFoldedIntoKernel >= 0 && bound != lengthFoldedIntoKernel) {
            // Checked before anything is written, so a refused rebind leaves the view exactly as it
            // was rather than half-updated.
            throw new IllegalStateException("This view's length is visible to the kernel and " + lengthFoldedIntoKernel
                    + " has already been compiled into it, so it cannot now be bound with " + bound + " elements. "
                    + "Leave setLengthVisibleToKernel off and pass the length to the kernel as a parameter instead.");
        }
        Object token = holder == null ? NO_OWNER : holder;
        Object current = owner.compareAndExchange(null, token);
        if (current != null && current != token) {
            throw new IllegalStateException("This device view is already bound by another holder; a view belongs to one task at a time");
        }
        this.devicePointer = address;
        this.deviceByteLength = byteLength;
        this.deviceIndex = targetDeviceIndex;
        this.readOnly = readOnlyBinding;
        this.logicalLength = bound;
    }

    /** Releases the binding. The device memory is the caller's and is not freed here. */
    public void unbind(Object holder) {
        Object token = holder == null ? NO_OWNER : holder;
        Object current = owner.get();
        if (current != null && current != token) {
            throw new IllegalStateException("This device view is bound by another holder and cannot be unbound here");
        }
        this.devicePointer = 0;
        this.deviceByteLength = 0;
        this.deviceIndex = -1;
        this.readOnly = false;
        owner.set(null);
    }

    /** The bound address, failing with a clear message when the view was never bound. */
    public long requireBoundPointer() {
        long address = devicePointer;
        if (address == 0) {
            throw new IllegalStateException("This device view has no device address bound: call bindCUDADeviceBuffer(view, address, bytes) before executing the plan");
        }
        return address;
    }

    @Override
    public String toString() {
        return "CUDADeviceViewBinding[capacity=" + capacityElements + " elements of " + elementSize + "B, logicalLength=" + logicalLength + (isBound()
                ? ", bound to 0x" + Long.toHexString(devicePointer) + " (" + deviceByteLength + "B, device " + deviceIndex + (readOnly ? ", read-only" : ", read-write") + ")"
                : ", unbound") + "]";
    }
}
