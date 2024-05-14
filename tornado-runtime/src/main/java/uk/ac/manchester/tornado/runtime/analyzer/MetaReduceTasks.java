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
 */
package uk.ac.manchester.tornado.runtime.analyzer;

import java.util.ArrayList;
import java.util.HashMap;

import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Mapping between the input tasks and the parameters indexes in which reduce
 * variables are found.
 *
 */
public class MetaReduceTasks {

    private HashMap<Integer, ArrayList<Integer>> reduceList;
    private HashMap<Integer, Integer> reduceSize;
    private StructuredGraph graph;

    MetaReduceTasks(int taskIndex, StructuredGraph graph, ArrayList<Integer> reduceIndexes, int inputSize) {
        reduceList = new HashMap<>();
        reduceSize = new HashMap<>();
        reduceList.put(taskIndex, reduceIndexes);
        reduceSize.put(taskIndex, inputSize);
        this.graph = graph;
    }

    public ArrayList<Integer> getListOfReduceParameters(int taskID) {
        return reduceList.get(taskID);
    }

    public int getInputSize(int taskIndex) {
        return reduceSize.get(taskIndex);
    }

    public StructuredGraph getGraph() {
        return graph;
    }
}
