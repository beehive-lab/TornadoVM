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
 * the raw device pointer of their TornadoVM-managed device buffer (past the
 * array header), primitive arguments carry their boxed value.
 */
public final class LibraryInvocation {

    private final Object[] javaArgs;
    private final long[] devicePointers;
    private final boolean[] isReference;
    private final TornadoXPUDevice device;
    private final long executionPlanId;
    private final LibraryContext context;
    private final Object tuning;

    public LibraryInvocation(Object[] javaArgs, long[] devicePointers, boolean[] isReference, TornadoXPUDevice device, long executionPlanId, LibraryContext context, Object tuning) {
        this.javaArgs = javaArgs;
        this.devicePointers = devicePointers;
        this.isReference = isReference;
        this.device = device;
        this.executionPlanId = executionPlanId;
        this.context = context;
        this.tuning = tuning;
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
     * The raw device pointer for a reference argument at the given position,
     * pointing at the first data element (past the TornadoVM array header).
     */
    public long getDevicePointer(int index) {
        return devicePointers[index];
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
}
