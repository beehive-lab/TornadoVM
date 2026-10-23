/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.PrebuiltTaskPackage;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.common.KernelAccess;

/**
 * Code analysis class for reductions in TornadoVM. The operations and the input size come from
 * the kernel's bytecode, through the compiler ({@link KernelAccess#reductions}).
 */
public class ReduceCodeAnalysis {

    /**
     * It obtains a list of reduce parameters for each task.
     *
     * @return {@link MetaReduceTasks}
     */
    public static MetaReduceCodeAnalysis analyzeTaskGraph(List<TaskPackage> taskPackages) {
        int taskIndex = 0;
        int inputSize = 0;

        HashMap<Integer, MetaReduceTasks> tableMetaDataReduce = new HashMap<>();

        for (TaskPackage taskMetadata : taskPackages) {

            if (taskMetadata instanceof PrebuiltTaskPackage) {
                // We skip analysis of pre-built tasks
                continue;
            }

            // A method task names its kernel directly and its parameter array holds only the
            // kernel's arguments, so there is no lambda in slot 0 to resolve or to skip over.
            final boolean methodTask = taskMetadata.isMethodTask();
            final int argOffset = methodTask ? 0 : 1;

            final Method kernel = methodTask ? taskMetadata.getMethod() : TaskUtils.resolveMethodHandle(taskMetadata.getTaskParameters()[0]);
            ArrayList<Integer> reduceIndices = new ArrayList<>();
            Annotation[][] declared = kernel.getParameterAnnotations();
            for (int paramIndex = 0; paramIndex < declared.length; paramIndex++) {
                for (Annotation annotation : declared[paramIndex]) {
                    if (annotation instanceof Reduce) {
                        reduceIndices.add(paramIndex);
                    }
                }
            }
            if (reduceIndices.isEmpty()) {
                taskIndex++;
                continue;
            }

            // A @Reduce parameter the kernel only reads has no operation: the task is still rewritten,
            // with no final reduction for it.
            var info = KernelAccess.reductions(kernel, reduceIndices).orElse(new KernelAccess.Reductions(List.of(), -1, -1, 0));
            List<REDUCE_OPERATION> operations = info.operations().stream().map(ReduceCodeAnalysis::operationOf).toList();
            if (info.boundParameter() >= 0) {
                Object bound = taskPackages.get(taskIndex).getTaskParameters()[info.boundParameter() + argOffset];
                if (bound instanceof TornadoNativeArray array) {
                    inputSize = array.getSize();
                } else if (bound.getClass().isArray()) {
                    inputSize = Array.getLength(bound);
                } else {
                    throw new TornadoRuntimeException("[ERROR] Unsupported type for reductions: " + bound.getClass());
                }
            } else if (info.boundConstant() >= 0) {
                inputSize = (int) info.boundConstant();
            }
            tableMetaDataReduce.put(taskIndex, new MetaReduceTasks(taskIndex, reduceIndices, inputSize, (int) info.start(), operations));
            taskIndex++;
        }

        return (tableMetaDataReduce.isEmpty() ? null : new MetaReduceCodeAnalysis(tableMetaDataReduce));
    }

    /** The operation TC names a reduction by. */
    static REDUCE_OPERATION operationOf(String name) {
        return switch (name) {
            case "add" -> REDUCE_OPERATION.SUM;
            case "mul" -> REDUCE_OPERATION.MUL;
            case "min" -> REDUCE_OPERATION.MIN;
            default -> REDUCE_OPERATION.MAX;
        };
    }

    public enum REDUCE_OPERATION { //
        SUM, //
        MUL, //
        MIN, //
        MAX //
    }
}
