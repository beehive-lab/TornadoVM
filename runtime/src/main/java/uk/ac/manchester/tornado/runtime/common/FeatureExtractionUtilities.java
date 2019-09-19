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

package uk.ac.manchester.tornado.runtime.common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.graalvm.compiler.nodes.StructuredGraph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FeatureExtractionUtilities {

    public static final String FEATURE_FILE = "tornado-features.txt";

    private static List<String> mathOps = new ArrayList<>(Arrays.asList("+", "-", "/", "*", "<<"));

    private FeatureExtractionUtilities() {
    }

    public static HashMap<String, Integer> prettyFormatFeatures(HashMap<String, Integer> feat) {
        HashMap<String, Integer> newFeat = new HashMap<>();

        newFeat.put("Global Memory Writes", (feat.get("Write") != null ? (feat.get("Write")) : 0));
        newFeat.put("Global Memory Reads", (feat.get("FloatingRead") != null ? (feat.get("FloatingRead")) : 0));
        newFeat.put("Total number of Loops", (feat.get("LoopBegin") != null ? (feat.get("LoopBegin")) : 0));
        newFeat.put("Parallel Loops", feat.get((feat.get("GlobalThreadId") != null ? (feat.get("GlobalThreadId")) : 0)));
        newFeat.put("If Statements", ((feat.get("LoopBegin") != null && feat.get("If") != null) ? (feat.get("If") - feat.get("LoopBegin")) : 0));
        newFeat.put("Switch Statements", feat.get((feat.get("IntegerSwitch") != null ? (feat.get("IntegerSwitch")) : 0)));
        newFeat.put("Vector Loads", (feat.get("VectorLoadElement") != null ? (feat.get("VectorLoadElement")) : 0));
        newFeat.put("Math Operations", mathOperations(feat));
        newFeat.put("Math Functions", (feat.get("OCLFPUnaryIntrinsic") != null ? feat.get("OCLFPUnaryIntrinsic") : 0));

        return newFeat;
    }

    private static Integer mathOperations(HashMap<String, Integer> feat) {
        Integer temp_ops = 0;

        for (String key : feat.keySet()) {
            if (mathOps.contains(key)) {
                temp_ops += feat.get(key);
            }
        }
        return temp_ops;
    }

    public static void emitJsonToFile(HashMap<String, Integer> entry, StructuredGraph grf) {
        Gson gsons = new GsonBuilder().setPrettyPrinting().create();
        String json = gsons.toJson(entry);
        File fil = new File(FEATURE_FILE);
        try (FileWriter file = new FileWriter(fil, RuntimeUtilities.ifFileExists(fil))) { // TO DO: FIX
            file.write(grf.name);
            file.write(json);
            file.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
