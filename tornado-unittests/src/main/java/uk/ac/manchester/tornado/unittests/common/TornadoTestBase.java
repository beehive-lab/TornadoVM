/*
 * Copyright (c) 2013-2020, 2022 APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoRuntimeInterface;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.tools.TornadoHelper;

public abstract class TornadoTestBase {

    protected static boolean wasDeviceInspected = false;

    @Before
    public void before() {
        for (int i = 0; i < TornadoRuntime.getTornadoRuntime().getNumDrivers(); i++) {
            final TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(i);
            for (int j = 0; j < driver.getDeviceCount(); j++) {
                driver.getDevice(j).reset();
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
                TornadoRuntime.getTornadoRuntime().setDefaultDriver(driverIndex);
            }
            int deviceIndex = pairDriverDevice.f1();
            if (deviceIndex != 0) {
                // We swap the default device for the selected one
                TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
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
        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();
        if (TornadoRuntime.getTornadoRuntime().getBackendType(driverIndex) == backend) {
            switch (backend) {
                case PTX:
                    throw new TornadoVMPTXNotSupported("Test not supported for the PTX backend");
                case OPENCL:
                    throw new TornadoVMOpenCLNotSupported("Test not supported for the OpenCL backend");
                case SPIRV:
                    throw new TornadoVMSPIRVNotSupported("Test not supported for the SPIR-V backend");
            }
        }
    }

    public void assertNotBackendOptimization(TornadoVMBackendType backend) {
        if (!TornadoHelper.OPTIMIZE_LOAD_STORE_SPIRV) {
            return;
        }
        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();
        if (TornadoRuntime.getTornadoRuntime().getBackendType(driverIndex) == backend) {
            if (backend == TornadoVMBackendType.SPIRV) {
                throw new SPIRVOptNotSupported("Test not supported for the optimized SPIR-V BACKEND");
            }
        }
    }

    private void assertIfNeeded(TornadoDevice device, int driverIndex) {
        TornadoVMBackendType backendType = TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getBackendType();
        if (backendType != TornadoVMBackendType.OPENCL || !device.isSPIRVSupported()) {
            assertNotBackend(TornadoVMBackendType.OPENCL);
        }
    }

    protected TornadoDevice checkSPIRVSupport() {
        TornadoDevice device = null;
        TornadoTestBase.Tuple2<Integer, Integer> driverAndDeviceIndex = getDriverAndDeviceIndex();
        if (driverAndDeviceIndex.f0() != 0) {
            // If another device has been selected for testing, TornadoVM has swapped with
            // another device using the position 0 for the selected device. In this case, we
            // select the chosen device instead of looking for a device with SPIRV support.
            device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
            assertIfNeeded(device, 0);
        } else {
            // Check if SPIRV is supported. We search for a suitable device to run on
            int numDrivers = TornadoRuntime.getTornadoRuntime().getNumDrivers();
            for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
                if (TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getBackendType() != TornadoVMBackendType.PTX) {
                    int maxDevices = TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getDeviceCount();
                    for (int i = 0; i < maxDevices; i++) {
                        // Search for the device with SPIRV Support
                        device = TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getDevice(i);
                        if (device.isSPIRVSupported()) {
                            return device;
                        }
                    }
                }
            }
        }
        return device;
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

    public static TornadoRuntimeInterface getTornadoRuntime() {
        return TornadoRuntime.getTornadoRuntime();
    }

}
