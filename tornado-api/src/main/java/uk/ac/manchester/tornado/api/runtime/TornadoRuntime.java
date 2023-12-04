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

import uk.ac.manchester.tornado.api.TornadoCI;
import uk.ac.manchester.tornado.api.TornadoRuntimeInterface;

public class TornadoRuntime {

    private static TornadoRuntimeInterface runtimeImpl;
    private static TornadoCI tornadoImpl;

    static {
        init();
    }

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadRuntime();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornado();
        }
    }

    public static TornadoRuntimeInterface getTornadoRuntime() {
        return runtimeImpl;
    }

    public static boolean isProfilerEnabled() {
        return runtimeImpl.isProfilerEnabled();
    }

    public void clearObjectState() {
        runtimeImpl.clearObjectState();
    }

    public static void setProperty(String key, String value) {
        tornadoImpl.setTornadoProperty(key, value);
    }

    public static String getProperty(String key, String value) {
        return tornadoImpl.getTornadoProperty(key, value);
    }

    public static String getProperty(String key) {
        return tornadoImpl.getTornadoProperty(key);
    }

    public static void loadSettings(String property) {
        tornadoImpl.loadTornadoSettings(property);
    }

}
