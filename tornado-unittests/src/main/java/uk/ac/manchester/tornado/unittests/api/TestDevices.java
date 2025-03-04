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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoDeviceMap;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * $ tornado-test -V uk.ac.manchester.tornado.unittests.api.TestDevices
 * </code>
 * </p>
 */
public class TestDevices extends TornadoTestBase {

    /**
     * We ask, on purpose, for a backend index that does not exist to
     * check that the exception {@link TornadoBackendNotFound} in thrown.
     */
    @Test(expected = TornadoBackendNotFound.class)
    public void test01() {
        TornadoDevice device = TornadoExecutionPlan.getDevice(100, 0);
    }

    /**
     * We ask, on purpose, for a device index that does not exist to
     * check that the exception {@link TornadoDeviceNotFound} in thrown.
     */
    @Test(expected = TornadoDeviceNotFound.class)
    public void test02() {
        TornadoDevice device = TornadoExecutionPlan.getDevice(0, 100);
    }

    /**
     * Test the {@link TornadoDeviceMap} API to obtain the devices without
     * requiring the developer to access through the runtime instance.
     *
     * <p>
     * The goal with this API is to allow developers to apply filters
     * and query the backend and device properties of the desired ones.
     * </p>
     */
    @Test
    public void test03() {

        // Obtains an instance of a class that contains a map with all backends and Devices
        // that the develop can query. 
        TornadoDeviceMap tornadoDeviceMap = TornadoExecutionPlan.getTornadoDeviceMap();

        // Query the number of backends
        int numBackends = tornadoDeviceMap.getNumBackends();

        assertTrue(numBackends >= 1);

        // Query all backends
        List<TornadoBackend> backends = tornadoDeviceMap.getAllBackends();

        assertFalse(backends.isEmpty());

        // Query the number of devices that are accessible per backend
        int numDevicesBackendZero = backends.getFirst().getNumDevices();

        assertTrue(numDevicesBackendZero >= 1);

        // Obtain a reference to a device within the selected backend
        TornadoDevice device = backends.getFirst().getDevice(0);

        assertNotNull(device);

    }

    /**
     * Test to check different examples of how can we apply filters to obtain the desired backends and devices
     * depending on input filters.
     */
    @Test
    public void test04() {

        assertNotBackend(TornadoVMBackendType.SPIRV);

        TornadoDeviceMap tornadoDeviceMap = TornadoExecutionPlan.getTornadoDeviceMap();

        // Query the number of backends
        int numBackends = tornadoDeviceMap.getNumBackends();

        assertTrue(numBackends >= 1);

        List<TornadoBackend> openCLBackend = tornadoDeviceMap.getBackendsWithPredicate(backend -> backend.getBackendType() == TornadoVMBackendType.OPENCL);

        assertNotNull(openCLBackend);

        // Obtain all backends with at least two devices associated to it
        List<TornadoBackend> multiDeviceBackends = tornadoDeviceMap.getBackendsWithPredicate(backend -> backend.getNumDevices() > 1);

        // If the multi-backend list is not empty, then it found at least one backend with multiple devices
        assertTrue(multiDeviceBackends.size() >= 1);

        // Obtain the backend that can support SPIR-V as default device
        List<TornadoBackend> spirvSupported = tornadoDeviceMap.getBackendsWithPredicate(backend -> backend.getDefaultDevice().isSPIRVSupported());

        // Return all backends that can access an NVIDIA GPU
        List<TornadoBackend> backendsWithNVIDIAAccess = tornadoDeviceMap.getBackendsWithDevicePredicate(device -> device //
                .getPhysicalDevice() //
                .getDeviceName()     //
                .toLowerCase()       //
                .contains("nvidia"));

        assertFalse(backendsWithNVIDIAAccess.isEmpty());

        // Another way to perform the previous query
        List<TornadoBackend> backendsWithNVIDIAAccess2 = tornadoDeviceMap //
                .getBackendsWithPredicate(backend -> backend //
                        .getAllDevices()//
                        .stream()//
                        .anyMatch(device -> device//
                                .getPhysicalDevice()//
                                .getDeviceName()    //
                                .toLowerCase()      //
                                .contains("nvidia")));

        assertFalse(backendsWithNVIDIAAccess2.isEmpty());

    }

    @Test
    public void test05() {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        TornadoDeviceMap deviceMap = new TornadoDeviceMap();
        Stream<TornadoDevice> deviceStream = deviceMap.getDevicesByName("NVIDIA");

        // Get the first that meets the criteria
        TornadoDevice device = deviceStream.findFirst().get();
        assertNotNull(device);
        assertTrue(device.getPhysicalDevice().getDeviceName().toLowerCase().contains("nvidia"));
    }

    @Test
    public void test06() {
        assertNotBackend(TornadoVMBackendType.PTX);

        TornadoDeviceType typeToFind = TornadoDeviceType.CPU;
        TornadoDeviceMap deviceMap = new TornadoDeviceMap();
        Stream<TornadoDevice> deviceStream = deviceMap.getDevicesByType(typeToFind);

        // Get the first that meets the criteria
        TornadoDevice device = deviceStream.findFirst().get();
        assertNotNull(device);
        assertSame(typeToFind, device.getDeviceType());
    }

    @Test
    public void test07() {
        TornadoDeviceMap deviceMap = new TornadoDeviceMap();

        // Intentionally a random name to get an empty stream
        Stream<TornadoDevice> deviceStream = deviceMap.getDevicesByName("foo");
        assertTrue(deviceStream.findAny().isEmpty());
    }
}
