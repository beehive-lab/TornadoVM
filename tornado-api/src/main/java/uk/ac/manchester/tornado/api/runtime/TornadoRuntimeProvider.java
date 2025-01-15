/*
 * Copyright (c) 2013-2025, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.TornadoSetting;

public class TornadoRuntimeProvider {

    private static TornadoRuntime runtimeImpl;
    private static TornadoSetting tornadoImpl;

    static {
        init();
    }

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadTornadoRuntimeImpl();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornadoImpl();
        }
    }

    public static TornadoRuntime getTornadoRuntime() {
        return runtimeImpl;
    }

    public static boolean isProfilerEnabled() {
        return runtimeImpl.isProfilerEnabled();
    }

    public static boolean isPowerMonitoringEnabled() {
        return runtimeImpl.isPowerMonitoringEnabled();
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
        tornadoImpl.loadTornadoProperty(property);
    }

}
