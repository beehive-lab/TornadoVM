/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.types.arrays;

import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorByte;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorDouble;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorFloat;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorInt;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorShort;

public abstract sealed class TornadoNativeArray permits //
        IntArray, FloatArray, DoubleArray, LongArray, ShortArray, //
        ByteArray, CharArray, NativeVectorByte, NativeVectorDouble, //
        NativeVectorShort, NativeVectorFloat, NativeVectorInt {
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", "24"));

    public abstract int getSize();

    public abstract MemorySegment getSegment();

    public abstract long getNumBytesOfSegment();

    public abstract long getNumBytesWithoutHeader();

    protected abstract void clear();

}
