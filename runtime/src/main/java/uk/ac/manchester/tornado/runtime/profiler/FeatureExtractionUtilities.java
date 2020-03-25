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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.utils.JsonHandler;

public class FeatureExtractionUtilities {

    private static final String FEATURE_FILE = "tornado-features.json";
    private static final String LOOKUP_BUFFER_ADDRESS_NAME = "compile-kernellookupBufferAddress";

    private static List<String> arithmeticOps = new ArrayList<>(Arrays.asList("+", "-", "/", "*", "<<", "%"));

    private FeatureExtractionUtilities() {
    }

    private static Integer mathOperations(HashMap<String, Integer> feat) {
        Integer sumMathOperations;
        if ((feat.get("OCLFPUnaryIntrinsic") != null) && feat.get("OCLIntBinaryIntrinsic") != null) {
            sumMathOperations = feat.get("OCLFPUnaryIntrinsic") + feat.get("OCLFPUnary  Intrinsic");
        } else if (feat.get("OCLFPUnaryIntrinsic") != null) {
            sumMathOperations = feat.get("OCLFPUnaryIntrinsic");
        } else if (feat.get("OCLIntBinaryIntrinsic") != null) {
            sumMathOperations = feat.get("OCLIntBinaryIntrinsic");
        } else {
            sumMathOperations = 0;
        }
        return sumMathOperations;
    }

    private static Integer arithmeticOperations(HashMap<String, Integer> feat) {
        Integer sumOperations = 0;
        for (String key : feat.keySet()) {
            if (arithmeticOps.contains(key)) {
                sumOperations += feat.get(key);
            }
        }
        return sumOperations;
    }

    public static void emitFeatureProfiletoJsonFile(LinkedHashMap<ProfilerCodeFeatures, Integer> entry, String name) {
        name = name.split("-")[1];
        if (!name.equals(LOOKUP_BUFFER_ADDRESS_NAME)) {
            HashMap<String, HashMap<String, Integer>> task = new HashMap<>();
            task.put(name, encodeMap(entry));
            JsonHandler jsonHandler = new JsonHandler();
            String json = jsonHandler.createJSon(encodeMap(entry), name);
            File fileLog = new File(FEATURE_FILE);
            try (FileWriter file = new FileWriter(fileLog, RuntimeUtilities.ifFileExists(fileLog))) {
                file.write(json);
                file.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static LinkedHashMap<String, Integer> encodeMap(LinkedHashMap<ProfilerCodeFeatures, Integer> entry) {
        LinkedHashMap<String, Integer> endcodeMap = new LinkedHashMap<>();

        for (Map.Entry<ProfilerCodeFeatures, Integer> ent : entry.entrySet()) {
            endcodeMap.put(ent.getKey().toString(), ent.getValue());
        }

        return endcodeMap;
    }

    public static LinkedHashMap<ProfilerCodeFeatures, Integer> createMap() {
        LinkedHashMap<ProfilerCodeFeatures, Integer> myMap = new LinkedHashMap<>();
        myMap.put(ProfilerCodeFeatures.GLOBAL_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.GLOBAL_STORES, 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_STORES, 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_STORES, 0);
        myMap.put(ProfilerCodeFeatures.PRIVATE_LOADS, 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_STORES, 0);
        myMap.put(ProfilerCodeFeatures.LOOPS, 0);
        myMap.put(ProfilerCodeFeatures.PARALLEL_LOOPS, 0);
        myMap.put(ProfilerCodeFeatures.IFS, 0);
        myMap.put(ProfilerCodeFeatures.SWITCH, 0);
        myMap.put(ProfilerCodeFeatures.CASE, 0);
        myMap.put(ProfilerCodeFeatures.VECTORS, 0);
        myMap.put(ProfilerCodeFeatures.INTEGER, 0);
        myMap.put(ProfilerCodeFeatures.FLOATS, 0);
        myMap.put(ProfilerCodeFeatures.BINARY, 0);
        myMap.put(ProfilerCodeFeatures.CAST, 0);
        myMap.put(ProfilerCodeFeatures.I_CMP, 0);
        myMap.put(ProfilerCodeFeatures.F_CMP, 0);
        return myMap;
    }
}
