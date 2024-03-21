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

import org.junit.Test;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.api.exceptions.TornadoDriverNotFound;
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
     * We ask, on purpose, for a driver index that does not exist to
     * check that the exception {@link TornadoDriverNotFound} in thrown.
     */
    @Test(expected = TornadoDriverNotFound.class)
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

}
