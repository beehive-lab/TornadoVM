/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.collections;

import java.lang.foreign.MemorySegment;
import java.nio.Buffer;

import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

public sealed

interface TornadoCollectionInterface<T extends Buffer> //
        extends PrimitiveStorage<T>  //
permits VectorDouble, VectorDouble2, VectorDouble3, VectorDouble4, VectorDouble8, VectorDouble16, //
VectorFloat, VectorFloat2, VectorFloat3, VectorFloat4, VectorFloat8, VectorFloat16, //
VectorInt, VectorInt2, VectorInt3, VectorInt4, VectorInt8, VectorInt16, //
VectorHalf, VectorHalf2, VectorHalf3, VectorHalf4, VectorHalf8, VectorHalf16 {

    long getNumBytes();

    long getNumBytesWithHeader();

    MemorySegment getSegment();

    MemorySegment getSegmentWithHeader();
}
