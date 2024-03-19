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
import java.util.List;

import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Data structure to query and filter the TornadoVM Drivers and TornadoVM Devices that the TornadoVM can access.
 */
public class TornadoDeviceMap {

    private final TornadoRuntimeInterface runtime = TornadoRuntime.getTornadoRuntime();

    private final int numBackends;

    public TornadoDeviceMap() {
        numBackends = runtime.getNumDrivers();
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
     * @return {@link List<TornadoDriver>}
     */
    public List<TornadoDriver> getAllBackends() {
        List<TornadoDriver> drivers = new ArrayList<>();
        for (int i = 0; i < numBackends; i++) {
            drivers.add(runtime.getDriver(i));
        }
        return drivers;
    }
}