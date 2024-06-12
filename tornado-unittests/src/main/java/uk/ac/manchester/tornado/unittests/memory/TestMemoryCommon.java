/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.memory;

import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMemoryCommon extends TornadoTestBase {

    /**
     * Set the number of elements to select ~300MB per array.
     */
    static final int NUM_ELEMENTS = 78643200;   // 314MB for an array of Integers
    static IntArray a = new IntArray(NUM_ELEMENTS);
    static IntArray b = new IntArray(NUM_ELEMENTS);
    static IntArray c = new IntArray(NUM_ELEMENTS);

    static int value = 10000000;
}
