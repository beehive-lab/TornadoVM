/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.library.spi;

import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * A single library-task call with its arguments already resolved by the
 * TornadoVM interpreter: reference arguments (off-heap arrays/tensors) carry
 * the address of their first data element in the TornadoVM-managed buffer
 * (past the array header), plus the native buffer object and the element's
 * byte offset inside it; primitive arguments carry their boxed value.
 */
public final class LibraryInvocation {

    private final Object[] javaArgs;
    private final long[] devicePointers;
    private final long[] nativeBuffers;
    private final long[] nativeOffsets;
    private final boolean[] isReference;
    private final TornadoXPUDevice device;
    private final long executionPlanId;
    private final LibraryContext context;
    private final Object tuning;
    private final boolean capturing;

    public LibraryInvocation(Object[] javaArgs, long[] devicePointers, long[] nativeBuffers, long[] nativeOffsets, boolean[] isReference, TornadoXPUDevice device, long executionPlanId,
            LibraryContext context, Object tuning, boolean capturing) {
        this.javaArgs = javaArgs;
        this.devicePointers = devicePointers;
        this.nativeBuffers = nativeBuffers;
        this.nativeOffsets = nativeOffsets;
        this.isReference = isReference;
        this.device = device;
        this.executionPlanId = executionPlanId;
        this.context = context;
        this.tuning = tuning;
        this.capturing = capturing;
    }

    public int getNumArgs() {
        return javaArgs.length;
    }

    /**
     * The original Java argument at the given position (boxed primitive, or the
     * host-side array/tensor object for reference arguments).
     */
    public Object getArg(int index) {
        return javaArgs[index];
    }

    /**
     * The address of the first data element (past the TornadoVM array header) of
     * a reference argument: a device pointer on CUDA, and a CPU address of the
     * shared-storage buffer on Metal. See {@code XPUBuffer.libraryAddress()}.
     */
    public long getDevicePointer(int index) {
        return devicePointers[index];
    }

    /**
     * The backend's native buffer object for a reference argument: the device
     * allocation on CUDA, the MTLBuffer on Metal. Providers that bind buffers
     * rather than raw addresses (e.g. Metal compute encoders) combine it with
     * {@link #getNativeOffset(int)}.
     */
    public long getNativeBuffer(int index) {
        return nativeBuffers[index];
    }

    /**
     * Byte offset of the first data element inside {@link #getNativeBuffer(int)}.
     */
    public long getNativeOffset(int index) {
        return nativeOffsets[index];
    }

    public boolean isReference(int index) {
        return isReference[index];
    }

    public TornadoXPUDevice getDevice() {
        return device;
    }

    public long getExecutionPlanId() {
        return executionPlanId;
    }

    public LibraryContext getContext() {
        return context;
    }

    /**
     * Library-specific tuning options attached via
     * {@link uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor#withTuning(Object)},
     * or null. Opaque to the runtime; interpreted by the provider.
     */
    public Object getTuning() {
        return tuning;
    }

    /**
     * Whether this call is being recorded into a CUDA graph rather than executed
     * immediately. Device allocations, host synchronisation and stream queries are
     * not capture-safe, so a provider that would need one must reject the call
     * instead of performing it. Sizing work of that kind belongs in
     * {@link TornadoLibraryProvider#prepare}, which runs before the capture starts.
     */
    public boolean isCapturing() {
        return capturing;
    }
}
