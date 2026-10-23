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
 */
package uk.ac.manchester.tornado.runtime.analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Mapping between the input tasks and the parameters indexes in which reduce
 * variables are found.
 *
 */
public class MetaReduceTasks {

    private final HashMap<Integer, ArrayList<Integer>> reduceList;
    private final HashMap<Integer, Integer> reduceSize;
    private final List<ReduceCodeAnalysis.REDUCE_OPERATION> operations;
    private final int loopStart;

    MetaReduceTasks(int taskIndex, ArrayList<Integer> reduceIndexes, int inputSize, int loopStart, List<ReduceCodeAnalysis.REDUCE_OPERATION> operations) {
        this.loopStart = loopStart;
        reduceList = new HashMap<>();
        reduceSize = new HashMap<>();
        reduceList.put(taskIndex, reduceIndexes);
        reduceSize.put(taskIndex, inputSize);
        this.operations = operations;
    }

    public ArrayList<Integer> getListOfReduceParameters(int taskID) {
        return reduceList.get(taskID);
    }

    public int getInputSize(int taskIndex) {
        return reduceSize.get(taskIndex);
    }

    /** The constant start of the reduction loop: the iterations are {@code inputSize - loopStart}. */
    public int getLoopStart() {
        return loopStart;
    }

    /** The operations, one per reduce parameter. */
    public List<ReduceCodeAnalysis.REDUCE_OPERATION> getOperations() {
        return operations;
    }
}
