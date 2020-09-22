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
 * Authors: Michalis Papadimitriou 
 *
 *
 */

package uk.ac.manchester.tornado.runtime.profiler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.utils.JsonHandler;

public class FeatureExtractionUtilities {

    private static final String FEATURES_DIRECTORY = Tornado.getProperty("tornado.features.dir", "");
    private static final String FEATURE_FILE = Tornado.getProperty("tornado.features.filename", "tornado-features");
    private static final String LOOKUP_BUFFER_ADDRESS_NAME = "kernellookupBufferAddress";

    private FeatureExtractionUtilities() {
    }

    public static void emitFeatureProfileJsonFile(LinkedHashMap<ProfilerCodeFeatures, Integer> entry, String name, TornadoDeviceContext deviceContext) {
        name = name.split("-")[1];
        if (!name.equals(LOOKUP_BUFFER_ADDRESS_NAME)) {
            HashMap<String, HashMap<String, Integer>> task = new HashMap<>();
            task.put(name, encodeFeatureMap(entry));
            JsonHandler jsonHandler = new JsonHandler();
            String json = jsonHandler.createJSon(encodeFeatureMap(entry), name, deviceContext.getDeviceName());
            File fileLog = new File(FEATURES_DIRECTORY + FEATURE_FILE + ".json");
            try (FileWriter file = new FileWriter(fileLog, RuntimeUtilities.ifFileExists(fileLog))) {
                file.write(json);
                file.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
        myMap.put(ProfilerCodeFeatures.GLOBAL_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.GLOBAL_STORES, 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_STORES, 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_STORES, 0);
        myMap.put(ProfilerCodeFeatures.PRIVATE_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.PRIVATE_STORES, 0);
        myMap.put(ProfilerCodeFeatures.LOOPS, 0);
        myMap.put(ProfilerCodeFeatures.PARALLEL_LOOPS, 0);
        myMap.put(ProfilerCodeFeatures.IFS, 0);
        myMap.put(ProfilerCodeFeatures.I_CMP, 0);
        myMap.put(ProfilerCodeFeatures.F_CMP, 0);
        myMap.put(ProfilerCodeFeatures.SWITCH, 0);
        myMap.put(ProfilerCodeFeatures.CASE, 0);
        myMap.put(ProfilerCodeFeatures.VECTORS, 0);
        myMap.put(ProfilerCodeFeatures.INTEGER_OPS, 0);
        myMap.put(ProfilerCodeFeatures.FLOAT_OPS, 0);
        myMap.put(ProfilerCodeFeatures.FP32, 0);
        myMap.put(ProfilerCodeFeatures.DOUBLES, 0);
        myMap.put(ProfilerCodeFeatures.CAST, 0);
        myMap.put(ProfilerCodeFeatures.F_MATH, 0);
        myMap.put(ProfilerCodeFeatures.I_MATH, 0);
        return myMap;
    }
}
