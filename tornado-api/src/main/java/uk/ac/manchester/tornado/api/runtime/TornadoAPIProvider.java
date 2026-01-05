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
package uk.ac.manchester.tornado.api.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.TornadoSetting;
import uk.ac.manchester.tornado.api.TornadoTaskGraphInterface;
import uk.ac.manchester.tornado.api.exceptions.TornadoAPIException;

public class TornadoAPIProvider {

    public static TornadoTaskGraphInterface loadScheduleRuntime(String name) {
        TornadoTaskGraphInterface taskGraphImpl;
        try {
            String tornadoAPIImplementation = System.getProperty("tornado.load.api.implementation");
            if (tornadoAPIImplementation == null) {
                throw new TornadoAPIException("[ERROR] Tornado API Implementation class not specified. Did you remember to add @tornado-argfile?");
            }
            Class<?> klass = Class.forName(tornadoAPIImplementation);
            Constructor<?> constructor = klass.getConstructor(String.class);
            taskGraphImpl = (TornadoTaskGraphInterface) constructor.newInstance(name);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new TornadoAPIException("[ERROR] Tornado API Implementation class not found", e);
        }
        return taskGraphImpl;
    }

    public static TornadoRuntime loadTornadoRuntimeImpl() {
        TornadoRuntime runtime;
        try {
            String tornadoRuntimeImplementation = System.getProperty("tornado.load.runtime.implementation");
            if (tornadoRuntimeImplementation == null) {
                throw new TornadoAPIException("[ERROR] Tornado Runtime Implementation class not specified. Did you remember to add @tornado-argfile?");
            }
            Class<?> klass = Class.forName(tornadoRuntimeImplementation);
            Method method = klass.getDeclaredMethod("getTornadoRuntime");
            runtime = (TornadoRuntime) method.invoke(null);
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new TornadoAPIException("[ERROR] Tornado Runtime Implementation class not found", e);
        }
        return runtime;
    }

    public static TornadoSetting loadTornadoImpl() {
        TornadoSetting tornado;
        try {
            String tornadoImplementation = System.getProperty("tornado.load.tornado.implementation");
            if (tornadoImplementation == null) {
                throw new TornadoAPIException("[ERROR] Tornado Implementation class not specified. Did you remember to add @tornado-argfile?");
            }
            Class<?> klass = Class.forName(tornadoImplementation);
            Constructor<?> constructor = klass.getConstructor();
            tornado = (TornadoSetting) constructor.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new TornadoAPIException("[ERROR] Tornado Implementation class not found", e);
        }
        return tornado;
    }
}
