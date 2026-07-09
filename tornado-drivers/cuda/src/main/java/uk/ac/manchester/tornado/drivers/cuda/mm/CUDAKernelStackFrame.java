/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022-2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.mm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;

/**
 * Kernel stack frame backed by an OFF-HEAP pinned host buffer. The frame is written to
 * the device on every kernel launch, so the copy sits on the critical launch path: the
 * heap-{@code ByteBuffer} route would go through the Java-array JNI transfer, which must
 * synchronise the stream after every copy (the pinned critical region cannot outlive the
 * call) - one {@code cuStreamSynchronize} per launch, which also stalls host-side
 * enqueueing and breaks multi-stream pipelining. Off-heap storage takes the host-pointer
 * transfer path (async), and pinning the region makes the copy true DMA. The buffer is
 * owned by this frame: registered after allocation, unregistered (with a context sync)
 * and freed on {@link #invalidate()}.
 */
public class CUDAKernelStackFrame extends CUDAByteBuffer implements KernelStackFrame {

    public static final int RETURN_VALUE_INDEX = 0;
    public static final int RESERVED_SLOTS = 3;
    private static final int FRAME_BYTES = RESERVED_SLOTS << 3;
    private static final long HOST_BUFFER_ALIGNMENT = 64;

    private final ArrayList<CallArgument> callArguments;

    private boolean isValid;

    /** Off-heap host buffer backing {@code buffer}, or 0 when allocation fell back to heap. */
    private long hostPointer;

    CUDAKernelStackFrame(long bufferId, int numArgs, CUDADeviceContext device) {
        super(device, bufferId, 0, FRAME_BYTES);
        this.callArguments = new ArrayList<>(numArgs);
        CUDAContext platformContext = device.getPlatformContext();
        this.hostPointer = platformContext.allocateOffHeapMemory(FRAME_BYTES, HOST_BUFFER_ALIGNMENT);
        if (hostPointer != 0) {
            ByteBuffer direct = platformContext.asByteBuffer(hostPointer, FRAME_BYTES);
            direct.order(device.getByteOrder());
            buffer = direct;
            platformContext.registerPinnedMemory(hostPointer, FRAME_BYTES);
        }
        buffer.clear();
        this.isValid = true;
    }

    @Override
    public void addCallArgument(Object value, boolean isReferenceType) {
        callArguments.add(new CallArgument(value, isReferenceType));
    }

    @Override
    public void reset() {
        callArguments.clear();
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    @Override
    public void invalidate() {
        isValid = false;
        if (hostPointer != 0) {
            // Unregister (native side synchronises the context first) BEFORE freeing, so
            // no stale pin can survive the host buffer.
            deviceContext.getPlatformContext().unregisterPinnedMemory(hostPointer);
            deviceContext.getPlatformContext().freeOffHeapMemory(hostPointer);
            hostPointer = 0;
        }
        deviceContext.getPlatformContext().releaseBuffer(toBuffer());
    }

    /** Async device write from the off-heap (pinned) host frame: no per-launch stream sync. */
    @Override
    public int enqueueWrite(long executionPlanId, final int[] events) {
        if (hostPointer == 0) {
            return super.enqueueWrite(executionPlanId, events);
        }
        return deviceContext.enqueueWriteBuffer(executionPlanId, toBuffer(), getOffset(), FRAME_BYTES, hostPointer, 0, events);
    }

    /** Blocking device write from the off-heap host frame (used by the CUDA-graph capture flush). */
    @Override
    public void write(long executionPlanId, final int[] events) {
        if (hostPointer == 0) {
            super.write(executionPlanId, events);
            return;
        }
        deviceContext.writeBuffer(executionPlanId, toBuffer(), getOffset(), FRAME_BYTES, hostPointer, 0, events);
    }

    @Override
    public List<CallArgument> getCallArguments() {
        return callArguments;
    }

    @Override
    public void setKernelContext(HashMap<Integer, Integer> map) {
        buffer.clear();
        for (int i = 0; i < RESERVED_SLOTS; i++) {
            if (map.containsKey(i)) {
                buffer.putLong(map.get(i));
            } else {
                buffer.putLong(0);
            }
        }
    }
}
