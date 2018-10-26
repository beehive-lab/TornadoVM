/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.examples.reductions;

import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class Config {

    public static final int MAX_ITERATIONS = 101;

    public static TornadoDeviceType getDefaultDeviceType() {
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        return driver.getTypeDefaultDevice();
    }

    public static float[] allocResultArray(int numGroups) {
        TornadoDeviceType deviceType = getDefaultDeviceType();
        float[] result = null;
        switch (deviceType) {
            case CPU:
                result = new float[Runtime.getRuntime().availableProcessors()];
                break;
            case GPU:
            case ACCELERATOR:
                result = new float[numGroups];
                break;
            default:
                break;
        }
        return result;
    }

}
