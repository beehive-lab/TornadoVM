/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.natives;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.drivers.opencl.ffm.OpenCLAPI;

public class NativeCommandQueue {

    /** {@code CL_MAP_READ} / {@code CL_MAP_WRITE}. */
    private static final long CL_MAP_READ = 1L;
    private static final long CL_MAP_WRITE = 1L << 1;

    /** Mapping a source region onto a destination simply yields the source pointer. */
    public static long mapOnDeviceMemoryRegion(long destDevicePtr, long srcDevicePtr) {
        return srcDevicePtr;
    }

    /**
     * Copies the payload of one device buffer into another through host mappings, skipping the
     * TornadoVM segment header.
     *
     * <p>
     * {@code offset} and {@code headerSize} are both counts of elements, not bytes: the caller
     * passes {@code ARRAY_HEADER / sizeof(int)}. The JNI version this replaces mixed the two units,
     * subtracting the element count {@code headerSize} from the byte count {@code sizeDest} to size
     * the copy, which made every copy run past the end of the destination mapping -- three floats
     * for a 16-byte header. Nothing caught it, because C does not bounds-check a mapped pointer.
     * Here the payload is measured in bytes throughout, and the copy is clamped to what both
     * mappings actually hold.
     */
    public static long mapOnDeviceMemoryNDRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = FFMSupport.allocateInt(arena);
            long source = OpenCLAPI.clEnqueueMapBuffer(commandQueuePtr, srcDevicePtr, OpenCLAPI.CL_TRUE, CL_MAP_READ, 0, sizeSource, 0, MemorySegment.NULL, MemorySegment.NULL, status);
            long destination = OpenCLAPI.clEnqueueMapBuffer(commandQueuePtr, destDevicePtr, OpenCLAPI.CL_TRUE, CL_MAP_WRITE, 0, sizeDest, 0, MemorySegment.NULL, MemorySegment.NULL, status);
            if (source == 0 || destination == 0) {
                return destDevicePtr;
            }
            long headerBytes = headerSize * sizeDataType;
            long sourceOffset = offset * sizeDataType + headerBytes;
            long payloadBytes = Math.min(sizeDest - headerBytes, sizeSource - sourceOffset);
            if (payloadBytes > 0) {
                MemorySegment from = FFMSupport.asSegment(source, sizeSource).asSlice(sourceOffset, payloadBytes);
                MemorySegment to = FFMSupport.asSegment(destination, sizeDest).asSlice(headerBytes, payloadBytes);
                MemorySegment.copy(from, 0, to, 0, payloadBytes);
            }
            OpenCLAPI.clEnqueueUnmapMemObject(commandQueuePtr, destDevicePtr, destination, 0, MemorySegment.NULL, MemorySegment.NULL);
            return destDevicePtr;
        }
    }
}
