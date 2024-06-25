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
package uk.ac.manchester.tornado.api;

import java.util.List;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;

/**
 * Interface of the TornadoVM backends. This interface contains the minimum amount of methods to be query per backend,
 * such as the total number of devices, query the default device, etc.
 */
public interface TornadoBackend {

    /**
     * Returns the default device accelerator. If TornadoVM has access to multiple accelerators,
     * it will select one as default one. The default accelerator depends on the backends installed.
     * TornadoVM prioritizes compute capabilities and memory available to sort the list of devices.
     * The default device is the first in this internal list.
     * 
     * @return {@link TornadoDevice}
     */
    TornadoDevice getDefaultDevice();

    /**
     * Sets the default device. The default device is set in the first position of an internal linked list.
     * This method swaps the selected device to be used as default.
     *
     * @param index
     *     Device Index within the list of devices per backend.
     */
    void setDefaultDevice(int index);

    /**
     * Returns the total number of devices per backend. TornadoVM guarantees at least one device.
     * 
     * @return int
     */
    int getNumDevices();

    /**
     * Obtains a device object. Each backend could access multiple hardware accelerators.
     * This method selects one device that can be used to pass to an {@link TornadoExecutionPlan}
     * to run applications on.
     *
     * @param index
     *     Index within the device internal list.
     * @return {@link TornadoDevice}
     */
    TornadoDevice getDevice(int index);

    /**
     * Returns a list with all device objects available for the current backend.
     * 
     * @return {@link List<TornadoDevice>}
     */
    List<TornadoDevice> getAllDevices();

    /**
     * Returns the type of the default device. The device type could be CPU, GPU,
     * CUSTOM, FPGA.
     * 
     * @return {@link TornadoDeviceType}
     */
    TornadoDeviceType getTypeDefaultDevice();

    /**
     * Returns the name of the Backend. E.g., "OpenCL", "SPIR-V".
     * 
     * @return {@link String}
     */
    String getName();

    /**
     * Returns the total number of Platforms that are accessible within a specific Backend.
     * 
     * @return int
     */
    int getNumPlatforms();

    /**
     * Returns the type of the backend. Backends could be:
     * 
     * <ul>
     * <li>OpenCL: An OpenCL C Device</li>
     * <li>PTX: An NVIDIA Device with CUDA Support</li>
     * <li>SPIRV: An SPIR-V Device (OpenCL >= 2.1) </li>
     * <li>JAVA: A Java device. This might be removed in future versions</li>
     * <li>VIRTUAL: A Virtual Device</li>
     * </ul>
     * 
     * @return {@link TornadoVMBackendType}
     */
    TornadoVMBackendType getBackendType();

}
