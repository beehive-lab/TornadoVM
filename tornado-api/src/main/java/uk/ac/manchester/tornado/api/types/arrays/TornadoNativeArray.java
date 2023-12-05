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
package uk.ac.manchester.tornado.api.types.arrays;

import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorByte;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorDouble;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorFloat;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorInt;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorShort;

/**
 * This abstract sealed class represents the common functionality of the TornadoVM custom off-heap data structures,
 * i.e., native arrays ({@link ByteArray}, {@link IntArray}, etc.) and native vector collections ({@link NativeVectorByte},
 * {@link NativeVectorDouble}, etc.).
 *
 * <p>
 * The class provides methods for retrieving the number of elements stored in the native data structures,
 * for obtaining the underlying memory segment, for clearing the data, for calculating the total number of
 * bytes occupied by the memory segment, and for getting the number of bytes, excluding the array header size.
 * </p>
 *
 * <p>
 * The constant {@code ARRAY_HEADER} represents the size of the header in bytes.
 * </p>
 */
public abstract sealed class TornadoNativeArray permits //
        IntArray, FloatArray, DoubleArray, LongArray, ShortArray, //
        ByteArray, CharArray, NativeVectorByte, NativeVectorDouble, //
        NativeVectorShort, NativeVectorFloat, NativeVectorInt {

    /**
     * The size of the header in bytes. The default value is 24, but it can be configurable through
     * the "tornado.panama.objectHeader" system property.
     */
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", "24"));

    /**
     * Returns the number of elements stored in the native array or vector.
     * @return The number of elements of the native data structure.
     */
    public abstract int getSize();

    /**
     * Returns the underlying {@link MemorySegment} of the native data structure.
     * @return The {@link MemorySegment} associated with the native data structure instance.
     */
    public abstract MemorySegment getSegment();

    /**
     * Returns the total number of bytes that the {@link MemorySegment} occupies.
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    public abstract long getNumBytesOfSegment();

    /**
     * Returns the number of bytes of the {@link MemorySegment}, excluding the header bytes.
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    public abstract long getNumBytesWithoutHeader();

    /**
     * Clears the contents of the native data structure.
     */
    protected abstract void clear();

}
