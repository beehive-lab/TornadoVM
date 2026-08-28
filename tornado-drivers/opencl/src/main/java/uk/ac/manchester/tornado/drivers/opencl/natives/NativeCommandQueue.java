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

import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;
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
     * The offsets mirror the JNI version exactly, including its mixed units: {@code offset} and
     * {@code headerSize} index the mapped regions as {@code float} elements, while
     * {@code sizeDest} and {@code headerSize} are subtracted as bytes to give the element count.
     */
    public static long mapOnDeviceMemoryNDRegion(long commandQueuePtr, long destDevicePtr, long srcDevicePtr, long offset, int sizeDataType, long headerSize, long sizeSource, long sizeDest) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = FFMSupport.allocateInt(arena);
            long source = OpenCLAPI.clEnqueueMapBuffer(commandQueuePtr, srcDevicePtr, OpenCLAPI.CL_TRUE, CL_MAP_READ, 0, sizeSource, 0, MemorySegment.NULL, MemorySegment.NULL, status);
            long destination = OpenCLAPI.clEnqueueMapBuffer(commandQueuePtr, destDevicePtr, OpenCLAPI.CL_TRUE, CL_MAP_WRITE, 0, sizeDest, 0, MemorySegment.NULL, MemorySegment.NULL, status);
            if (source == 0 || destination == 0) {
                return destDevicePtr;
            }
            int elements = (int) ((sizeDest - headerSize) / sizeDataType);
            if (elements > 0) {
                MemorySegment from = FFMSupport.asSegment(source, sizeSource).asSlice((offset + headerSize) * Float.BYTES, (long) elements * Float.BYTES);
                MemorySegment to = FFMSupport.asSegment(destination, sizeDest).asSlice(headerSize * Float.BYTES, (long) elements * Float.BYTES);
                MemorySegment.copy(from, 0, to, 0, (long) elements * Float.BYTES);
            }
            OpenCLAPI.clEnqueueUnmapMemObject(commandQueuePtr, destDevicePtr, destination, 0, MemorySegment.NULL, MemorySegment.NULL);
            return destDevicePtr;
        }
    }
}
