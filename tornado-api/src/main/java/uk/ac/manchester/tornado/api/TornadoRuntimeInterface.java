/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.memory.TornadoGlobalObjectState;

public interface TornadoRuntimeInterface {

    void clearObjectState();

    TornadoDriver getDriver(int index);

    void setDefaultDriver(int index);

    TornadoVMBackendType getBackendType(int index);

    <D extends TornadoDriver> D getDriver(Class<D> type);

    int getNumDrivers();

    TornadoDevice getDefaultDevice();

    TornadoGlobalObjectState resolveObject(Object object);

    <D extends TornadoDriver> int getDriverIndex(Class<D> driverClass);

    boolean isProfilerEnabled();
}
