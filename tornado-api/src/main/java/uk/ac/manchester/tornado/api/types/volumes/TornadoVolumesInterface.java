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
package uk.ac.manchester.tornado.api.types.volumes;

import java.lang.foreign.MemorySegment;
import java.nio.Buffer;

import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

public sealed interface TornadoVolumesInterface<T extends Buffer> //
        extends PrimitiveStorage<T> permits VolumeShort2 {

    long getNumBytes();

    long getNumBytesWithHeader();

    MemorySegment getSegment();

    MemorySegment getSegmentWithHeader();

}
