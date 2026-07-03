/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx;

/**
 * Backend-neutral seam for a device command stream. Phase 1 of the CUDA-stream
 * work introduces this lightweight interface so the {@link StreamPool} and
 * {@link ExecutionStreamSet} can address streams by their lifecycle/identity
 * surface rather than the concrete {@link PTXStream}. The per-operation enqueue
 * overloads remain on {@link PTXStream}; this interface deliberately exposes only
 * what the pool and device context need generically, leaving room for the CUDA
 * and OpenCL implementations to drop in during later iterations.
 */
public interface Stream {

    /** The role this stream serves within an {@link ExecutionStreamSet}. */
    PTXStreamType getStreamType();

    /** Serialized native stream handle (CUstream wrapper). */
    byte[] getStreamHandle();

    /** Blocks the host until all work submitted to this stream has completed. */
    void sync();

    /** Drops all events tracked by this stream's event pool without host syncing. */
    void reset();

    /** Whether the underlying native stream has already been destroyed. */
    boolean isDestroy();

    /** Destroys the underlying native stream (and any pinned staging buffer). */
    void cuDestroyStream();

    /** The event pool backing this stream. */
    PTXEventPool getEventPool();
}
