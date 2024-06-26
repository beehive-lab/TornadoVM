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
package uk.ac.manchester.tornado.api.types.arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class TornadoMemorySegment {
    private MemorySegment segment;

    public TornadoMemorySegment(long segmentByteSize, int basedIndex, int numElements) {
        this.segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numElements);
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public void setSegmentAt(int index, float value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_FLOAT, baseIndex + index, value);
    }

    public float getSegmentFrom(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_FLOAT, baseIndex + index);
    }
}
