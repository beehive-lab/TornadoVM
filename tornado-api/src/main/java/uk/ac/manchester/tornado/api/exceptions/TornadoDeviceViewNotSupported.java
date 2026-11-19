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
package uk.ac.manchester.tornado.api.exceptions;

/**
 * Thrown when a device view is used on a backend that has not implemented device views, or on a
 * backend other than the one its device buffer was bound for.
 *
 * <p>
 * Binding borrows a device allocation another library owns: a device address on CUDA, an
 * {@code MTLBuffer} on Metal. The OpenCL backend has not implemented the same for a {@code cl_mem}
 * yet. The refusal is a deliberate one made before any kernel launches, so that a caller who can
 * do something else -- fall back to ordinary arrays, or to a CPU path -- can catch this and do it,
 * rather than discovering the problem when a kernel reads from an address the device cannot use.
 * </p>
 */
public class TornadoDeviceViewNotSupported extends RuntimeException {

    public TornadoDeviceViewNotSupported(final String msg) {
        super(msg);
    }
}
