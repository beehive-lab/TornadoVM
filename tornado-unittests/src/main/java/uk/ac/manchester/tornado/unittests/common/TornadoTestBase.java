/*
 * Copyright (c) 2013-2020, 2022, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.common;

import org.junit.Before;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.unittests.tools.TornadoHelper;

public abstract class TornadoTestBase {

    public static final float DELTA = 0.001f;
    public static final float DELTA_05 = 0.5f;
    protected static boolean wasDeviceInspected = false;

    public static TornadoRuntime getTornadoRuntime() {
        return TornadoRuntimeProvider.getTornadoRuntime();
    }

    @Before
    public void before() {
        for (int backendIndex = 0; backendIndex < TornadoRuntimeProvider.getTornadoRuntime().getNumBackends(); backendIndex++) {
            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(backendIndex);
            for (int deviceIndex = 0; deviceIndex < driver.getNumDevices(); deviceIndex++) {
                driver.getDevice(deviceIndex).clean();
            }
        }

        /*
         * Virtual Device execution assumes an environment with a single device.
         * Therefore, there is no need to change the device even if a different device
         * is set through the 'tornado.unittests.device' property
         */
        if (!wasDeviceInspected && !getVirtualDeviceEnabled()) {
            Tuple2<Integer, Integer> pairDriverDevice = getDriverAndDeviceIndex();
            int driverIndex = pairDriverDevice.f0();
            if (driverIndex != 0) {
                // We swap the default driver for the selected one
                TornadoRuntimeProvider.getTornadoRuntime().setDefaultBackend(driverIndex);
            }
            int deviceIndex = pairDriverDevice.f1();
            if (deviceIndex != 0) {
                // We swap the default device for the selected one
                TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0);
                driver.setDefaultDevice(deviceIndex);
            }
            wasDeviceInspected = true;
        }
    }

    private boolean getVirtualDeviceEnabled() {
        return Boolean.parseBoolean(System.getProperty("tornado.virtual.device", "False"));
    }

    protected Tuple2<Integer, Integer> getDriverAndDeviceIndex() {
        String driverAndDevice = System.getProperty("tornado.unittests.device", "0:0");
        String[] propertyValues = driverAndDevice.split(":");
        return new Tuple2<>(Integer.parseInt(propertyValues[0]), Integer.parseInt(propertyValues[1]));
    }

    public void assertNotBackend(TornadoVMBackendType backend) {
        assertNotBackend(backend, null);
    }

    public void assertNotBackend(TornadoVMBackendType backend, String customBackendAssertionMessage) {
        int driverIndex = getTornadoRuntime().getDefaultDevice().getDriverIndex();
        if (getTornadoRuntime().getBackendType(driverIndex) == backend) {
            switch (backend) {
                case PTX -> throw new TornadoVMPTXNotSupported(customBackendAssertionMessage != null ? customBackendAssertionMessage : "Test not supported for the PTX backend");
                case OPENCL -> throw new TornadoVMOpenCLNotSupported(customBackendAssertionMessage != null ? customBackendAssertionMessage : "Test not supported for the OpenCL backend");
                case SPIRV -> throw new TornadoVMSPIRVNotSupported(customBackendAssertionMessage != null ? customBackendAssertionMessage : "Test not supported for the SPIR-V backend");
                default -> throw new IllegalStateException(STR."Unexpected value for backend: \{backend}");
            }
        }
    }

    public void assertNotBackendOptimization(TornadoVMBackendType backend) {
        if (!TornadoHelper.OPTIMIZE_LOAD_STORE_SPIRV) {
            return;
        }
        int driverIndex = getTornadoRuntime().getDefaultDevice().getDriverIndex();
        if (getTornadoRuntime().getBackendType(driverIndex) == backend) {
            if (backend == TornadoVMBackendType.SPIRV) {
                throw new SPIRVOptNotSupported("Test not supported for the optimized SPIR-V BACKEND");
            }
        }
    }

    private void assertIfNeeded(TornadoDevice device, int driverIndex) {
        TornadoVMBackendType backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex).getBackendType();
        if (backendType != TornadoVMBackendType.OPENCL || !device.isSPIRVSupported()) {
            assertNotBackend(TornadoVMBackendType.OPENCL);
        }
    }

    /**
     * It returns a TornadoDevice that supports SPIR-V.
     *
     * @return {@link TornadoDevice} with SPIR-V support, or null if not found.
     */
    protected TornadoDevice getSPIRVSupportedDevice() {
        Tuple2<Integer, Integer> driverAndDeviceIndex = getDriverAndDeviceIndex();

        // Check if a specific device has been selected for testing
        if (driverAndDeviceIndex.f0() != 0) {
            TornadoBackend driver = getTornadoRuntime().getBackend(0);
            TornadoDevice device = driver.getDevice(0);
            assertIfNeeded(device, 0);
            return device;
        }

        // Search for a device with SPIR-V support. This method will return even an
        // OpenCL device if SPIR-V is supported.
        int numDrivers = getTornadoRuntime().getNumBackends();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            TornadoBackend driver = getTornadoRuntime().getBackend(driverIndex);
            if (driver.getBackendType() != TornadoVMBackendType.PTX) {
                int maxDevices = driver.getNumDevices();
                for (int i = 0; i < maxDevices; i++) {
                    TornadoDevice device = driver.getDevice(i);
                    if (device.isSPIRVSupported()) {
                        return device;
                    }
                }
            }
        }

        return null; // No device with SPIR-V support found
    }

    protected static class Tuple2<T0, T1> {
        T0 t0;
        T1 t1;

        public Tuple2(T0 first, T1 second) {
            this.t0 = first;
            this.t1 = second;
        }

        public T0 f0() {
            return t0;
        }

        public T1 f1() {
            return t1;
        }
    }

}
