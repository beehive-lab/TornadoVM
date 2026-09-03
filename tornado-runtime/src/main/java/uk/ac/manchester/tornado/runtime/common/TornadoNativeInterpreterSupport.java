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
 */
package uk.ac.manchester.tornado.runtime.common;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.types.collections.TornadoCollectionInterface;
import uk.ac.manchester.tornado.api.types.images.TornadoImagesInterface;
import uk.ac.manchester.tornado.api.types.matrix.TornadoMatrixInterface;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * Backend state changes needed when the bytecode interpreter allocates memory
 * directly through the native driver API.
 */
public interface TornadoNativeInterpreterSupport extends TornadoNativeStreamSupport {

    /**
     * Images, matrices and collections share one device layout on OpenCL, CUDA and
     * Metal: the serialised Java object, then its {@code TornadoNativeArray} payload.
     */
    static boolean canStageCompoundObject(Object object) {
        return object instanceof TornadoImagesInterface<?> //
                || object instanceof TornadoMatrixInterface<?> //
                || object instanceof TornadoCollectionInterface<?>;
    }

    static MemorySegment compoundPayload(Object reference) {
        return switch (reference) {
            case TornadoImagesInterface<?> image -> image.getSegmentWithHeader();
            case TornadoMatrixInterface<?> matrix -> matrix.getSegmentWithHeader();
            case TornadoCollectionInterface<?> collection -> collection.getSegmentWithHeader();
            default -> null;
        };
    }

    static MemorySegment ensureStagingBuffer(MemorySegment current, long bytes) {
        if (current == null || current.byteSize() != bytes) {
            return Arena.ofAuto().allocate(bytes, 1);
        }
        return current;
    }

    static long copyCompoundIn(byte[] objectHeader, long objectBytes, MemorySegment payload, MemorySegment staging) {
        MemorySegment.copy(MemorySegment.ofArray(objectHeader), 0, staging, 0, objectBytes);
        MemorySegment.copy(payload, 0, staging, objectBytes, payload.byteSize());
        return staging.address();
    }

    static void copyCompoundOut(MemorySegment staging, long objectBytes, byte[] objectHeader, MemorySegment payload) {
        MemorySegment.copy(staging, objectBytes, payload, 0, payload.byteSize());
        MemorySegment.copy(staging, 0, MemorySegment.ofArray(objectHeader), 0, objectBytes);
    }

    /** Returns whether this backend can expose the object's complete device layout to the native interpreter. */
    default boolean supportsNativeInterpreterObject(Object object) {
        return canStageCompoundObject(object);
    }

    /**
     * Prepares a stable, contiguous host view matching the object's device allocation and
     * returns its address. Called immediately before entering the native interpreter.
     */
    default long prepareNativeInterpreterHostBuffer(Object object, XPUDeviceBufferState state) {
        return 0L;
    }

    /** Copies a native transfer result from the contiguous host view back into the Java object. */
    default void completeNativeInterpreterHostBuffer(Object object, XPUDeviceBufferState state) {
    }

    /** Creates Java buffer metadata, but does not allocate device memory. */
    void prepareNativeAllocation(Object object, long batchSize, XPUDeviceBufferState state, Access access);

    /** Attaches a device handle returned by the native ALLOC handler. */
    void attachNativeAllocation(XPUDeviceBufferState state, Access access, long handle, long bytes);

    /** Releases Java state after native DEALLOC, or after a prepared ALLOC is cancelled. */
    void detachNativeAllocation(XPUDeviceBufferState state, Access access, long handle);
}
