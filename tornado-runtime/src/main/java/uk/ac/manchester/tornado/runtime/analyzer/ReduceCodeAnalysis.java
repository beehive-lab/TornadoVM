/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
            var info = KernelAccess.reductions(kernel, reduceIndices).orElse(new KernelAccess.Reductions(List.of(), -1, -1));
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
            tableMetaDataReduce.put(taskIndex, new MetaReduceTasks(taskIndex, reduceIndices, inputSize, operations));
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
