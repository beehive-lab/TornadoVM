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
package uk.ac.manchester.tornado.api.types.vectors;

import java.nio.Buffer;

import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

public sealed interface TornadoVectorsInterface<T extends Buffer> //
        extends PrimitiveStorage<T> //
                permits Byte3, Byte4, //
        Double2, Double3, Double4, Double8, Double16, //
        Float2, Float3, Float4, Float8, Float16, //
        Int2, Int3, Int4, Int8, Int16, //
        Short2, Short3, Half2, Half3, Half4, Half8, Half16 {
    long getNumBytes();

}
