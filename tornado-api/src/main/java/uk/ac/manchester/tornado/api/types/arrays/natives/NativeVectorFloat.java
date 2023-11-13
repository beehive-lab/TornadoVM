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

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

public class NativeVectorFloat extends TornadoNativeArray {
    private final int FLOAT_BYTES = 4;
    private MemorySegment segment;
    private int numberOfElements;

    private long segmentByteSize;

    // TODO: Make this constructor private. Then we need to fix the benchmarks and KFusion not to access through this method, but rather to build from a primitive object array.
    public NativeVectorFloat(int numElements) {
        segmentByteSize = numElements * FLOAT_BYTES;
        this.numberOfElements = numElements;
        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
    }

    public void set(int index, float value) {
        segment.setAtIndex(JAVA_FLOAT, index, value);
    }

    public float get(int index) {
        return segment.getAtIndex(JAVA_FLOAT, index);
    }

    public void init(float value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_FLOAT, i, value);
        }
    }

    @Override
    public int getSize() {
        return numberOfElements;
    }

    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }

    @Override
    public long getNumBytesWithoutHeader() {
        return segmentByteSize;
    }

    @Override
    protected void clear() {
        init(0.0f);
    }
}
