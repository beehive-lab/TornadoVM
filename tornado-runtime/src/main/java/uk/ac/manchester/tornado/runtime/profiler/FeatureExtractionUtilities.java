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
 * Authors: Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.runtime.profiler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.options.ProfilerFlags;

import uk.ac.manchester.tornado.runtime.utils.JsonHandler;

public final class FeatureExtractionUtilities {

    private static final String FEATURES_DIRECTORY = ProfilerFlags.FEATURES_DUMP_DIR.get();
    private static final String LOOKUP_BUFFER_ADDRESS_NAME = "kernellookupBufferAddress";

    private FeatureExtractionUtilities() {
    }

    /**
     * Writes the features of kernel {@code name} ({@code Class.method}) compiled for {@code deviceContext}
     * as JSON: appended to {@code tornado.features.dump.dir} when set, else printed.
     */
    public static void emitFeatureProfileJsonFile(LinkedHashMap<ProfilerCodeFeatures, Integer> entry, String name, TornadoDeviceContext deviceContext) {
        if (name.endsWith("." + LOOKUP_BUFFER_ADDRESS_NAME)) {
            return;
        }
        String json = new JsonHandler().createJSon(encodeFeatureMap(entry), name, deviceContext);
        if (!FEATURES_DIRECTORY.isEmpty()) {
            File fileLog = new File(FEATURES_DIRECTORY);
            try (FileWriter file = new FileWriter(fileLog, fileLog.exists())) {
                file.write(json);
                file.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(json);
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
