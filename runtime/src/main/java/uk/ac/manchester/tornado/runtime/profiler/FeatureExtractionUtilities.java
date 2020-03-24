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

import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.utils.JsonHandler;

public class FeatureExtractionUtilities {

    private static final String FEATURE_FILE = "tornado-features.json";

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

    public static void emitJsonToFile(LinkedHashMap<String, Integer> entry, String name) {
        if (!name.equals("compile-kernellookupBufferAddress")) {
            HashMap<String, HashMap<String, Integer>> task = new HashMap<>();
            task.put(name, entry);
            JsonHandler jsonHandler = new JsonHandler();
            String json = jsonHandler.createJSon(entry, name);
            File fileLog = new File(FEATURE_FILE);
            try (FileWriter file = new FileWriter(fileLog, RuntimeUtilities.ifFileExists(fileLog))) {
                file.write(json);
                file.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static LinkedHashMap<String, Integer> createMap() {
        LinkedHashMap<String, Integer> myMap = new LinkedHashMap<>();
        myMap.put(ProfilerCodeFeatures.GLOBAL_LOADS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.GLOBAL_STORES.toString(), 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_LOADS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.CONSTANT_STORES.toString(), 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_LOADS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_STORES.toString(), 0);
        myMap.put(ProfilerCodeFeatures.PRIVATE_LOADS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.LOCAL_STORES.toString(), 0);
        myMap.put(ProfilerCodeFeatures.LOOPS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.PARALLEL_LOOPS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.IFS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.SWITCH.toString(), 0);
        myMap.put(ProfilerCodeFeatures.CASE.toString(), 0);
        myMap.put(ProfilerCodeFeatures.VECTORS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.INTEGER.toString(), 0);
        myMap.put(ProfilerCodeFeatures.FLOATS.toString(), 0);
        myMap.put(ProfilerCodeFeatures.BINARY.toString(), 0);
        myMap.put(ProfilerCodeFeatures.I_CMP.toString(), 0);
        myMap.put(ProfilerCodeFeatures.F_CMP.toString(), 0);
        return myMap;
    }
}
