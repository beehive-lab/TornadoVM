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
package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;

public final class TensorInt64 extends TornadoNativeArray implements AbstractTensor {
    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public MemorySegment getSegment() {
        return null;
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return null;
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return 0;
    }

    @Override
    public long getNumBytesOfSegment() {
        return 0;
    }

    @Override
    protected void clear() {

    }

    @Override
    public int getElementSize() {
        return 0;
    }

    @Override
    public Shape getShape() {
        return null;
    }

    @Override
    public String getDTypeAsString() {
        return null;
    }

    @Override
    public DType getDType() {
        return null;
    }
}
