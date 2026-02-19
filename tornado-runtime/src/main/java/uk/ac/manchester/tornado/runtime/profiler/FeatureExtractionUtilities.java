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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 */

package uk.ac.manchester.tornado.runtime.profiler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.graal.compiler.nodes.StructuredGraph;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoVMClient;
import uk.ac.manchester.tornado.runtime.utils.JsonHandler;

public final class FeatureExtractionUtilities {

    private static final String FEATURES_DIRECTORY = Tornado.getProperty("tornado.features.dump.dir", "");
    private static final String LOOKUP_BUFFER_ADDRESS_NAME = "kernellookupBufferAddress";

    private FeatureExtractionUtilities() {
    }

    public static void emitFeatureProfileJsonFile(LinkedHashMap<ProfilerCodeFeatures, Integer> entry, StructuredGraph graph, TornadoDeviceContext deviceContext) {
        String name = graph.name.split("-")[1];

        if (!name.equals(LOOKUP_BUFFER_ADDRESS_NAME)) {
            HashMap<String, HashMap<String, Integer>> task = new HashMap<>();
            String fullName = getBaseClass(graph.method().getDeclaringClass().toClassName()) + "." + name;
            task.put(fullName, encodeFeatureMap(entry));
            JsonHandler jsonHandler = new JsonHandler();
            String json = jsonHandler.createJSon(encodeFeatureMap(entry), fullName, deviceContext);
            if (!FEATURES_DIRECTORY.isEmpty()) {
                File fileLog = new File(FEATURES_DIRECTORY);
                try {
                    try (FileWriter file = new FileWriter(fileLog, fileLog.exists())) {
                        file.write(json);
                        file.write("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println(json);
            }

            if (!TornadoOptions.SOCKET_PORT.isEmpty()) {
                TornadoVMClient tornadoVMClient = new TornadoVMClient();
                try {
                    tornadoVMClient.sentLogOverSocket(json);
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
        }
    }

    private static String getBaseClass(String fullDeclaredClass) {
        String[] baseClass = fullDeclaredClass.split("\\.");
        String baseCl = baseClass[baseClass.length - 1];

        if (baseCl.contains("$")) {
            baseCl = baseCl.split("\\$")[baseCl.length() - 1];
        }
        return baseCl;
    }

    private static LinkedHashMap<String, Integer> encodeFeatureMap(LinkedHashMap<ProfilerCodeFeatures, Integer> entry) {
        LinkedHashMap<String, Integer> encodeMap = new LinkedHashMap<>();
        for (Map.Entry<ProfilerCodeFeatures, Integer> ent : entry.entrySet()) {
            encodeMap.put(ent.getKey().toString(), ent.getValue());
        }
        return encodeMap;
    }

    public static LinkedHashMap<ProfilerCodeFeatures, Integer> initializeFeatureMap() {
        LinkedHashMap<ProfilerCodeFeatures, Integer> myMap = new LinkedHashMap<>();
        for (ProfilerCodeFeatures feature : ProfilerCodeFeatures.values()) {
            myMap.put(feature, 0);
        }
        return myMap;
    }
}
