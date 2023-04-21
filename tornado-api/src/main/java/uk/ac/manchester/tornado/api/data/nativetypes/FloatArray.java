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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.data.nativetypes;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

public class FloatArray {
    private MemorySegment segment;
    private final int FLOAT_BYTES = 4;

    private int numberOfElements;

    public FloatArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        segment = MemorySegment.allocateNative(numberOfElements * FLOAT_BYTES);
    }


    public void set(int index, float value) {
        MemoryAccess.setFloatAtIndex(segment, index, value);
    }

    public float get(int index) {
        return MemoryAccess.getFloatAtIndex(segment, index);
    }


    public void init(float value) {
        for (int i = 0; i < segment.byteSize() / FLOAT_BYTES; i++) {
            MemoryAccess.setFloatAtIndex(segment, i, value);
        }
    }

    public int getSize() {
        return numberOfElements;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public String toString() {
        String arrayContents = String.valueOf(this.get(0));
        for (int i = 1; i < numberOfElements; i++) {
            arrayContents += ", " + this.get(i);
        }
        return arrayContents;
    }
}
