/*
 * Copyright (c) 2013-2023, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.memory;

import java.util.List;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;

public interface XPUBuffer {

    class XPUBufferWrapper {
        public final long buffer;
        public long bufferOffset;

        public XPUBufferWrapper(long buffer, long bufferOffset) {
            this.buffer = buffer;
            this.bufferOffset = bufferOffset;
        }
    }

    long toBuffer();

    void setBuffer(XPUBufferWrapper bufferWrapper);

    long getBufferOffset();

    void read(long executionPlanId, Object reference);

    int read(long executionPlanId, Object reference, long hostOffset, long partialReadSize, int[] events, boolean useDeps);

    void write(long executionPlanId, Object reference);

    int enqueueRead(long executionPlanId, Object reference, long hostOffset, int[] events, boolean useDeps);

    List<Integer> enqueueWrite(long executionPlanId, Object reference, long batchSize, long hostOffset, int[] events, boolean useDeps);

    void allocate(Object reference, long batchSize, Access access) throws TornadoOutOfMemoryException, TornadoMemoryException;

    void markAsFreeBuffer() throws TornadoMemoryException;

    long size();

    void setSizeSubRegion(long batchSize);

    long getSizeSubRegionSize();

    long deallocate();

    default int[] getIntBuffer() {
        return null;
    }

    default void setIntBuffer(int[] arr) {
    }

    void mapOnDeviceMemoryRegion(long executionPlanId, XPUBuffer srcPointer, long offset);

    int getSizeOfType();

}
