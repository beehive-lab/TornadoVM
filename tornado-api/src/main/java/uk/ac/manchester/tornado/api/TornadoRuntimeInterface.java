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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;

public interface TornadoRuntimeInterface {

    TornadoBackend getBackend(int index);

    void setDefaultBackend(int index);

    TornadoVMBackendType getBackendType(int index);

    <D extends TornadoBackend> D getBackend(Class<D> type);

    int getNumBackends();

    TornadoDevice getDefaultDevice();

    <D extends TornadoBackend> int getBackendIndex(Class<D> driverClass);

    boolean isProfilerEnabled();
}
