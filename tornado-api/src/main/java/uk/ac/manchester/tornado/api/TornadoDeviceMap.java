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
package uk.ac.manchester.tornado.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

/**
 * Data structure to query and filter the TornadoVM Drivers and TornadoVM Devices that the TornadoVM Runtime can access.
 */
public class TornadoDeviceMap {

    private final TornadoRuntime coreRuntime = TornadoRuntimeProvider.getTornadoRuntime();

    private final int numBackends;

    private final List<TornadoBackend> backends;

    public TornadoDeviceMap() {
        numBackends = coreRuntime.getNumBackends();
        // build the list of backends
        backends = new ArrayList<>();
        for (int i = 0; i < numBackends; i++) {
            backends.add(coreRuntime.getBackend(i));
        }
    }

    /**
     * Method to return the total number of backends that are accessible from the TornadoVM Runtime.
     * 
     * @return int
     */
    public int getNumBackends() {
        return numBackends;
    }

    /**
     * Returns a list with all backends that are accessible from the TornadoVM Runtime.
     * 
     * @return {@link List< TornadoBackend >}
     */
    public List<TornadoBackend> getAllBackends() {
        return backends;
    }

    /**
     * Returns a list of backends that correspond to a filter given by a predicate.
     * 
     * @param predicate
     *     {@link Predicate<? super TornadoBackend > predicate}
     * @return {@link List< TornadoBackend >}
     */
    public List<TornadoBackend> getBackendsWithPredicate(Predicate<? super TornadoBackend> predicate) {
        return getAllBackends().stream().filter(predicate).toList();
    }

    /**
     * Return a list of backends that corresponds to a filter applied to a predicate that queries and filters devices within each backend.
     *
     * <p>
     * Examples of queries:
     * - Return all backends which devices have more than 4GB.
     * - Return all backends that can access to an NVIDIA or Intel device.
     * </p>
     * 
     * @param predicate
     *     {@link Predicate<? super TornadoDevice> predicate}
     * @return {@link List< TornadoBackend >}
     */
    public List<TornadoBackend> getBackendsWithDevicePredicate(Predicate<? super TornadoDevice> predicate) {
        return getAllBackends().stream().filter(backend -> backend.getAllDevices().stream().anyMatch(predicate)).toList();
    }

    /**
     * Obtain a device object with a specific name. This function returns a Stream of all devices that meet the criteria.
     * The device objects could be from different backends. For example, if we have multiple backends installed, and we
     * request a device name with "NVIDIA" in it. In this case, this function can return a stream with multiple devices
     * from different backends pointing to the same device (e.g., an NVIDIA GPU using the OpenCL backend, and an NVIDIA
     * GPU using the PTX backend).
     *
     * @param deviceName
     *     Name of the device.
     * @return
     *     Stream of devices that meet the criteria. Otherwise, an empty stream.
     */
    public Stream<TornadoDevice> getDevicesByName(String deviceName) {
        TornadoDeviceMap deviceMap = TornadoExecutionPlan.getTornadoDeviceMap();
        List<TornadoBackend> backendFilter = deviceMap.getBackendsWithPredicate(backend -> //
        backend.getAllDevices() //
                .stream() //
                .anyMatch(device -> device //
                        .getPhysicalDevice() //
                        .getDeviceName() //
                        .toLowerCase() //
                        .contains(deviceName.toLowerCase())));

        if (!backendFilter.isEmpty()) {
            Stream<TornadoDevice> deviceStream = null;
            for (TornadoBackend backend : backendFilter) {
                Stream<TornadoDevice> deviceStreamI = backend //
                        .getAllDevices() //
                        .stream() //
                        .filter(device -> device //
                                .getPhysicalDevice()//
                                .getDeviceName() //
                                .toLowerCase() //
                                .contains(deviceName.toLowerCase())); //

                if (deviceStream == null) {
                    deviceStream = deviceStreamI;
                } else {
                    deviceStream = Stream.concat(deviceStream, deviceStreamI);
                }
            }
            return deviceStream;
        }
        // Return an empty stream
        return Arrays.stream(new TornadoDevice[0]);
    }

    /**
     * Obtain a device object from a specific type (GPU, Accelerator, CPU). This function returns a Stream of all devices that meet the criteria.
     * The device objects could be from different backends. For example, if we have multiple backends installed, and we request any GPU device,
     * this function will return all GPU devices (GPU-OpenCL, GPU-SPIR-V, GPU-PTX), even if they point to the exact same device.
     * 
     * @param deviceType
     *     {@link TornadoDeviceType}
     * @return
     *     Stream of devices that meet the criteria. Otherwise, an empty stream.
     */
    public Stream<TornadoDevice> getDevicesByType(TornadoDeviceType deviceType) {
        TornadoDeviceMap deviceMap = TornadoExecutionPlan.getTornadoDeviceMap();
        List<TornadoBackend> backendFilter = deviceMap.getBackendsWithPredicate(backend -> //
        backend.getAllDevices() //
                .stream() //
                .anyMatch(device -> device.getDeviceType() == deviceType));

        if (!backendFilter.isEmpty()) {
            Stream<TornadoDevice> deviceStream = null;
            for (TornadoBackend backend : backendFilter) {
                Stream<TornadoDevice> deviceStreamI = backend //
                        .getAllDevices() //
                        .stream() //
                        .filter(device -> device.getDeviceType() == deviceType);

                if (deviceStream == null) {
                    deviceStream = deviceStreamI;
                } else {
                    deviceStream = Stream.concat(deviceStream, deviceStreamI);
                }
            }
            return deviceStream;
        }
        // Return an empty stream
        return Arrays.stream(new TornadoDevice[0]);
    }
}
