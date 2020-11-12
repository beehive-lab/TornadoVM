/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *
 */
package uk.ac.manchester.tornado.runtime.utils;

import java.util.HashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class JsonHandler {

    private final String DEVICE_ID = "Device ID";
    private final String DEVICE = "Device";
    private final String IP = "IP";

    private StringBuffer indent;

    private void increaseIndent() {
        indent.append("    ");
    }

    private void decreaseIndent() {
        indent.delete(0, 4);
    }

    public String createJSon(HashMap<String, Integer> entry, String name, TornadoDeviceContext device) {
        indent = new StringBuffer();
        StringBuffer json = new StringBuffer("");
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString() + "\"" + name + "\": { \n");
        increaseIndent();
        if (TornadoOptions.LOG_IP) {
            json.append(indent.toString() + "\"" + IP + "\"" + ": " + "\"" + RuntimeUtilities.getTornadoInstanceIP() + "\",\n");
        }
        json.append(indent.toString() + "\"" + DEVICE_ID + "\":  \"" + device.getDriverIndex() + ":" + device.getDevicePlatform() + "\",\n");
        json.append(indent.toString() + "\"" + DEVICE + "\":  \"" + device.getDeviceName() + "\",\n");
        for (String s : entry.keySet()) {
            json.append(indent.toString() + "\"" + s + "\":  \"" + entry.get(s) + "\",\n");
        }
        json.delete(json.length() - 2, json.length() - 1); // remove last comma
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        return json.toString();
    }
}
