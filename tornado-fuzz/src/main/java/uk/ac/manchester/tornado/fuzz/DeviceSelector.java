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
package uk.ac.manchester.tornado.fuzz;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

/**
 * Locates the CUDA-C backend device. The fuzzer targets only the CUDA backend,
 * so it fails fast with a clear message if that backend is not present in the
 * running TornadoVM build.
 */
public final class DeviceSelector {

    private DeviceSelector() {
    }

    /**
     * @return the first device of the CUDA backend.
     * @throws IllegalStateException if no CUDA backend is available.
     */
    public static TornadoDevice cudaDevice() {
        var runtime = TornadoRuntimeProvider.getTornadoRuntime();
        int numBackends = runtime.getNumBackends();
        StringBuilder available = new StringBuilder();
        for (int i = 0; i < numBackends; i++) {
            TornadoVMBackendType type = runtime.getBackendType(i);
            available.append(i).append('=').append(type).append(' ');
            if (type == TornadoVMBackendType.CUDA) {
                TornadoBackend backend = runtime.getBackend(i);
                if (backend.getNumDevices() == 0) {
                    throw new IllegalStateException("CUDA backend present but exposes no devices");
                }
                return backend.getDevice(0);
            }
        }
        throw new IllegalStateException("No CUDA backend found. Build with `make BACKEND=cuda`. Backends seen: " + available.toString().trim());
    }
}
