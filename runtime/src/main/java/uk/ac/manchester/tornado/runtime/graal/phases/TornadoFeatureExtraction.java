/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.Collections;
import java.util.HashMap;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

public class TornadoFeatureExtraction extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        HashMap<String, Integer> features = new HashMap<String, Integer>();

        for (Node node : graph.getNodes()) {
            Integer j = features.get(node.asNode().getNodeClass().shortName());
            features.put(node.asNode().getNodeClass().shortName(), (j == null) ? 1 : j + 1);
        }
        //System.out.println(Collections.singletonList(features));

        System.out.println("Global Memory Writes: " + features.get("Write"));
        System.out.println("Global Memory Reads: " + features.get("FloatingRead"));
        System.out.println("Local Memory Reads: " + " X X X ");
        System.out.println("Local Memory Writes: " + " X X X");
        System.out.println("Vector Operations: " + (features.get("VectorLoadElement") != null));
        System.out.println("Number of Loops: " + features.get("LoopBegin"));
        System.out.println("Number of Parallel Loops: " + features.get("GlobalThreadId"));
        System.out.println("Number of Branches: " + ((features.get("LoopBegins") != null && features.get("If") != null) ? (features.get("If") - features.get("LoopBegin")) : 0));
        System.out.println("Number of Switch Statements: " + features.get("IntegerSwitch"));
        // System.out.println("Number of Math Operations: " + mathOperations);
        System.out.println("Number of Math Operations: " + "X X X ");
        System.out.println("Number of Math Functions: " + "X X X ");
    }

}
