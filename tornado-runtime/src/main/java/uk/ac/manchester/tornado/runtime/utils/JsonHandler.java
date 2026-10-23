/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.utils;

import java.util.Map;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class JsonHandler {

    private static final String SEPARATOR = "\":  \"";
    private static final String END_LINE = "\",\n";
    private static final String DEVICE_ID = "Device ID";
    private static final String DEVICE = "Device";
    private static final String IP = "IP";
    private StringBuilder indent;

    private void increaseIndent() {
        indent.append("    ");
    }

    private void decreaseIndent() {
        indent.delete(0, 4);
    }

    public String createJSon(Map<String, Integer> entry, String name, TornadoDeviceContext device) {
        indent = new StringBuilder();
        StringBuilder json = new StringBuilder("");
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString() + "\"" + name + "\": { \n");
        increaseIndent();
        if (TornadoOptions.LOG_IP) {
            json.append(indent.toString() + "\"" + IP + "\"" + ": " + "\"" + RuntimeUtilities.getTornadoInstanceIP() + END_LINE);
        }
        TornadoVMBackendType backendType = TornadoRuntimeProvider.getTornadoRuntime().getBackend(device.getDriverIndex()).getBackendType();
        json.append(indent.toString() + "\"" + ProfilerType.BACKEND + "\" : \"" + backendType + END_LINE);
        json.append(indent.toString() + "\"" + DEVICE_ID + SEPARATOR + device.getDriverIndex() + ":" + device.getDevicePlatform() + END_LINE);
        json.append(indent.toString() + "\"" + DEVICE + SEPARATOR + device.getDeviceName() + END_LINE);
        for (String s : entry.keySet()) {
            json.append(indent.toString() + "\"" + s + SEPARATOR + entry.get(s) + END_LINE);
        }
        json.delete(json.length() - 2, json.length() - 1); // remove last comma
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        return json.toString();
    }
}
