/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.hip;

import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.drivers.hip.enums.HIPDeviceType;

public interface HIPTargetDevice extends TornadoTargetDevice {

    long getDevicePointer();

    String getVersion();

    int getIndex();

    int getWordSize();

    ByteOrder getByteOrder();

    boolean isDeviceDoubleFPSupported();

    String getDeviceExtensions();

    HIPDeviceType getDeviceType();

    String getDeviceVendor();

    String getDriverVersion();

    boolean isDeviceAvailable();

    String getDeviceOpenCLCVersion();

    boolean isLittleEndian();

    HIPDeviceContextInterface getDeviceContext();

    void setDeviceContext(HIPDeviceContextInterface deviceContext);

    int deviceVersion();

    /** The AMD GPU architecture kernels are compiled for ({@code gfx942}), without feature suffixes. */
    String getArchitecture();

    /**
     * Number of asynchronous DMA copy engines ({@code hipDeviceAttributeAsyncEngineCount}).
     * {@code 0} = copies cannot overlap compute; {@code 1} = one direction can overlap compute;
     * {@code >= 2} = H2D and D2H can both overlap compute and each other. This is the hardware ceiling
     * on the number of useful role-based transfer streams. Virtual devices report {@code 0}.
     */
    default int getAsyncEngineCount() {
        return 0;
    }

    /**
     * Whether the device can execute multiple kernels from the same context concurrently
     * ({@code hipDeviceAttributeConcurrentKernels}). Virtual devices report {@code false}.
     */
    default boolean supportsConcurrentKernels() {
        return false;
    }
}
