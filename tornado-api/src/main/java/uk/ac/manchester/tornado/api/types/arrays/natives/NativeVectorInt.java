/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.arrays.natives;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

public class NativeVectorInt extends TornadoNativeArray {
    private final int INT_BYTES = 4;
    private int numberOfElements;
    private MemorySegment segment;
    private long segmentByteSize;

    public NativeVectorInt(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        segmentByteSize = numberOfElements * INT_BYTES;
        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
    }

    public void set(int index, int value) {
        segment.setAtIndex(JAVA_INT, index, value);
    }

    public int get(int index) {
        return segment.getAtIndex(JAVA_INT, index);
    }

    public void init(int value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_INT, i, value);
        }
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }

    @Override
    public void clear() {
        init(0);
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public long getNumBytesWithoutHeader() {
        return segmentByteSize;
    }
}
