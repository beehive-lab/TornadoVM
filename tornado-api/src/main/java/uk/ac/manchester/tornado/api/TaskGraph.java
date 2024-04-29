/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.PrebuiltTaskPackage;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task11;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task12;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task13;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task14;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoAPIProvider;

/**
 * Tornado Task Graph API.
 * <p>
 * Task-based parallel API to express methods to be accelerated on any OpenCL,
 * PTX or SPIR-V compatible device.
 * </p>
 *
 * @since TornadoVM-0.15
 */
public class TaskGraph implements TaskGraphInterface {

    private static final String ERROR_TASK_NAME_DUPLICATION = //
            "[TornadoVM ERROR]. There are more than 1 tasks with the same task-name. Use different a different task name for each task within " + "a TaskGraph.";

    private final String taskGraphName;
    protected TornadoTaskGraphInterface taskGraphImpl;
    protected HashSet<String> taskNames;

    public TaskGraph(String name) {
        this.taskGraphName = name;
        taskGraphImpl = TornadoAPIProvider.loadScheduleRuntime(name);
        taskNames = new HashSet<>();
    }

    private void checkTaskName(String id) {
        if (taskNames.contains(id)) {
            throw new TornadoTaskRuntimeException(ERROR_TASK_NAME_DUPLICATION);
        }
        taskNames.add(id);
    }

    /**
     * It adds a task by using a {@link TaskPackage}.
     *
     * @param taskPackage
     *     {@link uk.ac.manchester.tornado.api.common.TaskPackage}
     * @return {@link @TaskGraph}
     */
    @Override
    public TaskGraph addTask(TaskPackage taskPackage) {
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with no parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph task(String id, Task code) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Adds task with one parameter.
     *
     * @param id
     *     Task-id
     * @param code
     *     Reference to an existing Java method with one argument
     * @param arg
     *     Argument to the method
     * @return {@link TaskGraph}
     */
    @Override
    public <T1> TaskGraph task(String id, Task1<T1> code, T1 arg) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2> TaskGraph task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3> TaskGraph task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4> TaskGraph task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5> TaskGraph task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6> TaskGraph task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7> TaskGraph task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskGraph task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskGraph task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskGraph task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskGraph task(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskGraph task(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskGraph task(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskGraph task(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskGraph task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        checkTaskName(id);
        TaskPackage taskPackage = TaskPackage.createPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
        taskGraphImpl.addTask(taskPackage);
        return this;
    }

    /**
     * Add a pre-built OpenCL task into a task-schedule.
     *
     * @param id
     *     Task-Id
     * @param entryPoint
     *     Name of the method to be executed on the target device
     * @param filename
     *     Input file with the source kernel
     * @param args
     *     Arguments to the kernel
     * @param accesses
     *     Accesses ({@link uk.ac.manchester.tornado.api.common.Access} for
     *     each input parameter to the method
     * @param device
     *     Device to be executed
     * @param dimensions
     *     Select number of dimensions of the kernel (1D, 2D or 3D)
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions) {
        checkTaskName(id);
        TaskPackage prebuiltTask = TaskPackage.createPrebuiltTask(id, entryPoint, filename, args, accesses, device, dimensions);
        taskGraphImpl.addPrebuiltTask(prebuiltTask);
        return this;
    }

    /**
     * Add a pre-built OpenCL task into a task-schedule with atomics region.
     *
     * @param id
     *     Task-id
     * @param entryPoint
     *     Kernel's name of the entry point
     * @param filename
     *     Input OpenCL C Kernel
     * @param args
     *     Arguments to the method that the kernel represents.
     * @param accesses
     *     Array of access of each parameter to the kernel
     * @param device
     *     Device in which the OpenCL C code will be executed.
     * @param dimensions
     *     Select the dimension of the OpenCL kernel (1D, 2D or 3D)
     * @param atomics
     *     Atomics region.
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions, int[] atomics) {
        checkTaskName(id);
        PrebuiltTaskPackage prebuiltTask = TaskPackage.createPrebuiltTask(id, entryPoint, filename, args, accesses, device, dimensions);
        prebuiltTask.withAtomics(atomics);
        taskGraphImpl.addPrebuiltTask(prebuiltTask);
        return this;
    }

    /**
     * Obtains the task-schedule name that was assigned.
     *
     * @return {@link String}
     */
    @Override
    public String getTaskGraphName() {
        return taskGraphName;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph transferToDevice(final int mode, Object... objects) {
        taskGraphImpl.transferToDevice(mode, objects);
        return this;
    }

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
     * @return {@link TaskGraph}
     */
    @Override
    public TaskGraph transferToHost(final int mode, Object... objects) {
        taskGraphImpl.transferToHost(mode, objects);
        return this;
    }

    /**
     * Function that closes a task-graph definition and creates an immutable
     * task-graph ready for execution.
     *
     * @return {@link ImmutableTaskGraph}
     */
    @Override
    public ImmutableTaskGraph snapshot() {
        TaskGraph cloneTaskGraph = new TaskGraph(this.getTaskGraphName());
        cloneTaskGraph.taskGraphImpl = this.taskGraphImpl.createImmutableTaskGraph();
        cloneTaskGraph.taskNames = this.taskNames;
        return new ImmutableTaskGraph(cloneTaskGraph);
    }

    TaskGraph withDevice(TornadoDevice device) {
        taskGraphImpl.setDevice(device);
        return this;
    }

    TaskGraph withDevice(String taskName, TornadoDevice device) {
        taskGraphImpl.setDevice(taskName, device);
        return this;
    }

    TaskGraph batch(String batchSize) {
        taskGraphImpl.withBatch(batchSize);
        return this;
    }

    TaskGraph withMemoryLimit(String memoryLimit) {
        taskGraphImpl.withMemoryLimit(memoryLimit);
        return this;
    }

    public void withoutMemoryLimit() {
        taskGraphImpl.withoutMemoryLimit();
    }

    void execute(ExecutorFrame executionPackage) {
        taskGraphImpl.execute(executionPackage).waitOn();
    }

    void warmup() {
        taskGraphImpl.warmup();
    }

    void dumpEvents() {
        taskGraphImpl.dumpEvents();
    }

    void dumpTimes() {
        taskGraphImpl.dumpTimes();
    }

    void dumpProfiles() {
        taskGraphImpl.dumpProfiles();
    }

    void clearProfiles() {
        taskGraphImpl.clearProfiles();
    }

    TaskGraph freeDeviceMemory() {
        taskGraphImpl.freeDeviceMemory();
        return this;
    }

    void syncRuntimeTransferToHost(Object... objects) {
        taskGraphImpl.syncRuntimeTransferToHost(objects);
    }

    void syncRuntimeTransferToHost(Object object, long offset, long partialCopySize) {
        taskGraphImpl.syncRuntimeTransferToHost(object, offset, partialCopySize);
    }

    TornadoDevice getDevice() {
        return taskGraphImpl.getDevice();
    }

    TaskGraph useDefaultThreadScheduler(boolean use) {
        taskGraphImpl.useDefaultThreadScheduler(use);
        return this;
    }

    boolean isFinished() {
        return taskGraphImpl.isFinished();
    }

    public Set<Object> getArgumentsLookup() {
        return taskGraphImpl.getArgumentsLookup();
    }

    // *************************************************
    // Profiler Interface
    // *************************************************
    long getTotalTime() {
        return taskGraphImpl.getTotalTime();
    }

    long getCompileTime() {
        return taskGraphImpl.getCompileTime();
    }

    long getTornadoCompilerTime() {
        return taskGraphImpl.getTornadoCompilerTime();
    }

    long getDriverInstallTime() {
        return taskGraphImpl.getDriverInstallTime();
    }

    long getDataTransfersTime() {
        return taskGraphImpl.getDataTransfersTime();
    }

    long getWriteTime() {
        return taskGraphImpl.getDeviceWriteTime();
    }

    long getReadTime() {
        return taskGraphImpl.getDeviceReadTime();
    }

    long getDataTransferDispatchTime() {
        return taskGraphImpl.getDataTransferDispatchTime();
    }

    long getKernelDispatchTime() {
        return taskGraphImpl.getKernelDispatchTime();
    }

    long getDeviceKernelTime() {
        return taskGraphImpl.getDeviceKernelTime();
    }

    protected String getProfileLog() {
        return taskGraphImpl.getProfileLog();
    }

    public Collection<?> getOutputs() {
        return taskGraphImpl.getOutputs();
    }

    void enableProfiler(ProfilerMode profilerMode) {
        taskGraphImpl.enableProfiler(profilerMode);
    }

    void disableProfiler(ProfilerMode profilerMode) {
        taskGraphImpl.disableProfiler(profilerMode);
    }

    void withConcurrentDevices() {
        taskGraphImpl.withConcurrentDevices();
    }

    void withoutConcurrentDevices() {
        taskGraphImpl.withoutConcurrentDevices();
    }

    void withThreadInfo() {
        taskGraphImpl.withThreadInfo();
    }

    void withoutThreadInfo() {
        taskGraphImpl.withoutThreadInfo();
    }

    void withPrintKernel() {
        taskGraphImpl.withPrintKernel();
    }

    void withoutPrintKernel() {
        taskGraphImpl.withoutPrintKernel();
    }

    void withGridScheduler(GridScheduler gridScheduler) {
        taskGraphImpl.withGridScheduler(gridScheduler);
    }

}
