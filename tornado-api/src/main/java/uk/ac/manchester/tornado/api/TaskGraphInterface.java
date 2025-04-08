/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;

/**
 * Base interface of the Tornado API.
 *
 */
public interface TaskGraphInterface {

    /**
     * It adds a task by using a {@link TaskPackage}.
     *
     * @param taskPackage
     *     {@link uk.ac.manchester.tornado.api.common.TaskPackage}
     * @return {@link @TornadoAPI}
     */
    TaskGraphInterface addTask(TaskPackage taskPackage);

    /**
     * Adds task with no parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @return {@link TaskGraphInterface}
     */
    TaskGraphInterface task(String id, Task code);

    /**
     * Adds task with one parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @param arg
     *     Argument to the method
     * @return {@link TaskGraphInterface}
     */
    <T1> TaskGraphInterface task(String id, Task1<T1> code, T1 arg);

    /**
     * Adds task with two parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with two arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2> TaskGraphInterface task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2);

    /**
     * Add task with three parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with three arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3> TaskGraphInterface task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3);

    /**
     * Adds task with four parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with four arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4> TaskGraphInterface task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4);

    /**
     * Adds task with five parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with five arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5> TaskGraphInterface task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);

    /**
     * Adds task with six parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with six arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6> TaskGraphInterface task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);

    /**
     * Adds task with seven parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with seven arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7> TaskGraphInterface task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);

    /**
     * Adds task with eight parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with eight arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraphInterface task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);

    /**
     * Adds task with nine parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with nine arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraphInterface task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9);

    /**
     * Adds task with 10 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraphInterface task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10);

    /**
     * It creates a task with 11 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskGraphInterface task(String id, TornadoFunctions.Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11);

    /**
     * It creates a task with 12 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskGraphInterface task(String id, TornadoFunctions.Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12);

    /**
     * It creates a task with 13 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskGraphInterface task(String id, TornadoFunctions.Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13);

    /**
     * It creates a task with 14 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 10 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @param arg14
     *     Argument 14 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskGraphInterface task(String id, TornadoFunctions.Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14);

    /**
     * It creates a task with 15 parameters.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with 15 arguments
     * @param arg1
     *     Argument 1 to the method
     * @param arg2
     *     Argument 2 to the method
     * @param arg3
     *     Argument 3 to the method
     * @param arg4
     *     Argument 4 to the method
     * @param arg5
     *     Argument 5 to the method
     * @param arg6
     *     Argument 6 to the method
     * @param arg7
     *     Argument 7 to the method
     * @param arg8
     *     Argument 8 to the method
     * @param arg9
     *     Argument 9 to the method
     * @param arg10
     *     Argument 10 to the method
     * @param arg11
     *     Argument 11 to the method
     * @param arg12
     *     Argument 12 to the method
     * @param arg13
     *     Argument 13 to the method
     * @param arg14
     *     Argument 14 to the method
     * @param arg15
     *     Argument 15 to the method
     * @return {@link TaskGraphInterface}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskGraphInterface task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);

    TaskGraphInterface prebuiltTask(String id, String entryPoint, String filename, AccessorParameters accessorParameters);

    /**
     * Add a pre-built OpenCL task into a task-schedule with atomics region.
     *
     * @param id
     *     Task-id
     * @param entryPoint
     *     Kernel's name of the entry point
     * @param filename
     *     Input OpenCL C Kernel
     * @param atomics
     *     Atomics region.
     * @return {@link TaskGraphInterface}
     *
     */
    TaskGraphInterface prebuiltTask(String id, String entryPoint, String filename, AccessorParameters accessorParameters, int[] atomics);

    /**
     * Obtains the task-schedule name that was assigned.
     *
     * @return {@link String}
     */
    String getTaskGraphName();

    /**
     * Tag a set of objects (Java objects) to be transferred to the device. There
     * are three modes:
     *
     * <p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#FIRST_EXECUTION}:
     * it transfers data only the first execution of the task-graph (READ ONLY)
     * </p>
     *
     * </p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#EVERY_EXECUTION}:
     * it transfers data for every execution of the task-graph (READ/WRITE)
     * </p>
     *
     * @param mode
     *     A mode from
     *     {@link uk.ac.manchester.tornado.api.enums.DataTransferMode}
     * @param objects
     *     List of Java objects (usually arrays) to be transferred to the
     *     device.
     * @return {@link TaskGraphInterface}
     */
    TaskGraphInterface transferToDevice(int mode, Object... objects);


    TaskGraph consumeFromDevice(String uniqueTaskGraphName, Object... objects);

    TaskGraph consumeFromDevice(Object... objects);

    /**
     * Tag a set of objects (Java objects) to be transferred from the device to the
     * host after the execution completes. There are two modes:
     *
     * <p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#EVERY_EXECUTION}:
     * transfers data for every execution of the task-graph (WRITE only)
     * </p>
     *
     * </p>
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#UNDER_DEMAND}: it
     * transfers data only under demand. Data are not transferred unless the
     * execution-plan, an {@link TornadoExecutionPlan} object, invokes the
     * `transferToHost` function. This is used for optimization of data transfers.
     * </p>
     *
     * @param mode
     *     A mode from
     *     {@link uk.ac.manchester.tornado.api.enums.DataTransferMode}
     * @param objects
     *     List of Java objects (usually arrays) to be transferred to the
     *     device.
     * @return {@link TaskGraphInterface}
     */
    TaskGraphInterface transferToHost(int mode, Object... objects);

    TaskGraph persistOnDevice(Object... objects);

    /**
     * Function that closes a task-graph definition and creates an immutable
     * task-graph ready for execution.
     *
     * @return {@link ImmutableTaskGraph}
     */
    ImmutableTaskGraph snapshot();

}
