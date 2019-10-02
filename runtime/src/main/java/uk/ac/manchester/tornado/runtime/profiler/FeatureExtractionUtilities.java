/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

    public static LinkedHashMap<String, Integer> prettyFormatFeatures(HashMap<String, Integer> feat) {
        LinkedHashMap<String, Integer> newFeat = new LinkedHashMap<>();

        newFeat.put("Global Memory Reads", (feat.get("FloatingRead") != null ? (feat.get("FloatingRead")) : 0));
        newFeat.put("Global Memory Writes", (feat.get("Write") != null ? (feat.get("Write")) : 0));
        newFeat.put("Local Memory Reads", 0);
        newFeat.put("Local Memory Writes", 0);
        newFeat.put("Total number of Loops", (feat.get("LoopBegin") != null ? (feat.get("LoopBegin")) : 0));
        newFeat.put("Parallel Loops", (feat.get("GlobalThreadId") != null ? (feat.get("GlobalThreadId")) : 0));
        newFeat.put("If Statements", ((feat.get("LoopBegin") != null && feat.get("If") != null) ? (feat.get("If") - feat.get("LoopBegin")) : 0));
        newFeat.put("Switch Statements", (feat.get("IntegerSwitch") != null ? (feat.get("IntegerSwitch")) : 0));
        newFeat.put("Switch Cases", (feat.get("IntegerSwitch") != null ? (feat.get("SwitchCases")) : 0));
        newFeat.put("Vector Loads", (feat.get("VectorLoadElement") != null ? (feat.get("VectorLoadElement")) : 0));
        newFeat.put("Arithmetic Operations", arithmeticOperations(feat));
        newFeat.put("Math Operations", mathOperations(feat));

        return newFeat;
    }

    private static Integer mathOperations(HashMap<String, Integer> feat) {
        Integer sumMathOperations;
        if ((feat.get("OCLFPUnaryIntrinsic") != null) && feat.get("OCLIntBinaryIntrinsic") != null) {
            sumMathOperations = feat.get("OCLFPUnaryIntrinsic") + feat.get("OCLFPUnaryIntrinsic");
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
}
