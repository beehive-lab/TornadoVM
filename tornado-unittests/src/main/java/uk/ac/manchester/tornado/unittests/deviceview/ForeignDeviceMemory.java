/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.deviceview;

/**
 * Device memory allocated outside TornadoVM, through the backend's own API, standing in for a library
 * that owns its device memory. A {@code buffer} is what the backend binds to a view: a device address
 * on CUDA, an {@code id<MTLBuffer>} on Metal.
 */
interface ForeignDeviceMemory {

    long allocate(long bytes);

    void free(long buffer);

    void copyToDevice(long buffer, long byteOffset, float[] values);

    void copyToDevice(long buffer, long byteOffset, int[] values);

    float[] copyFloatsToHost(long buffer, long byteOffset, int count);

    /** Makes what TornadoVM's kernels wrote visible to the owner, as far as the owner is able to. */
    void synchronize();

    default void copyToDevice(long buffer, float[] values) {
        copyToDevice(buffer, 0, values);
    }

    default void copyToDevice(long buffer, int[] values) {
        copyToDevice(buffer, 0, values);
    }

    default float[] copyFloatsToHost(long buffer, int count) {
        return copyFloatsToHost(buffer, 0, count);
    }
}
