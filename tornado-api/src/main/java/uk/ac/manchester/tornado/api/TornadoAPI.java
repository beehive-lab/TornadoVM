/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 * 
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
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
 * Base interface of the Tornado API. It exposes the set of operations within a
 * task-schedule
 *
 */
public interface TornadoAPI {

    /**
     * It adds a task by using a {@link TaskPackage}.
     * 
     * @param taskPackage
     *            {@link uk.ac.manchester.tornado.api.common.TaskPackage}
     * @return {@link @TornadoAPI}
     */
    TornadoAPI addTask(TaskPackage taskPackage);

    /**
     * Add task with no parameter.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with one argument
     * @return {@link TornadoAPI}
     */
    TornadoAPI task(String id, Task code);

    /**
     * Add task with one parameter.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with one argument
     * @param arg
     *            Argument to the method
     * @return {@link TornadoAPI}
     */
    <T1> TornadoAPI task(String id, Task1<T1> code, T1 arg);

    /**
     * Add task with two parameters.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with two arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2> TornadoAPI task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2);

    /**
     * Add task with three parameters.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with three arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3> TornadoAPI task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3);

    /**
     * Add task with four parameters.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with four arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4> TornadoAPI task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4);

    /**
     * Add task with five parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with five arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5> TornadoAPI task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);

    /**
     * Add task with six parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with six arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6> TornadoAPI task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);

    /**
     * Add task with seven parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with seven arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7> TornadoAPI task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);

    /**
     * Add task with eight parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with eight arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8> TornadoAPI task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);

    /**
     * Add task with nine parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with nine arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9> TornadoAPI task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9);

    /**
     * Add task with 10 parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 10 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TornadoAPI task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9, T10 arg10);

    /**
     * It creates a task with 11 parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 10 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @param arg11
     *            Argument 11 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TornadoAPI task(String id, TornadoFunctions.Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11);

    /**
     * It creates a task with 12 parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 10 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @param arg11
     *            Argument 11 to the method
     * @param arg12
     *            Argument 12 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TornadoAPI task(String id, TornadoFunctions.Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12);

    /**
     * It creates a task with 13 parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 10 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @param arg11
     *            Argument 11 to the method
     * @param arg12
     *            Argument 12 to the method
     * @param arg13
     *            Argument 13 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TornadoAPI task(String id, TornadoFunctions.Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13);

    /**
     * It creates a task with 14 parameters.
     *
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 10 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @param arg11
     *            Argument 11 to the method
     * @param arg12
     *            Argument 12 to the method
     * @param arg13
     *            Argument 13 to the method
     * @param arg14
     *            Argument 14 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TornadoAPI task(String id, TornadoFunctions.Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14);

    /**
     * It creates a task with 15 parameters.
     * 
     * @param id
     *            Task-id
     * @param code
     *            Reference to an existing Java method with 15 arguments
     * @param arg1
     *            Argument 1 to the method
     * @param arg2
     *            Argument 2 to the method
     * @param arg3
     *            Argument 3 to the method
     * @param arg4
     *            Argument 4 to the method
     * @param arg5
     *            Argument 5 to the method
     * @param arg6
     *            Argument 6 to the method
     * @param arg7
     *            Argument 7 to the method
     * @param arg8
     *            Argument 8 to the method
     * @param arg9
     *            Argument 9 to the method
     * @param arg10
     *            Argument 10 to the method
     * @param arg11
     *            Argument 11 to the method
     * @param arg12
     *            Argument 12 to the method
     * @param arg13
     *            Argument 13 to the method
     * @param arg14
     *            Argument 14 to the method
     * @param arg15
     *            Argument 15 to the method
     * @return {@link TornadoAPI}
     */
    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TornadoAPI task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);

    /**
     * Add a pre-built OpenCL task into a task-schedule
     * 
     * @param id
     *            Task-Id
     * @param entryPoint
     *            Name of the method to be executed on the target device
     * @param filename
     *            Input file with the source kernel
     * @param args
     *            Arguments to the kernel
     * @param accesses
     *            Accesses ({@link uk.ac.manchester.tornado.api.common.Access} for
     *            each input parameter to the method
     * @param device
     *            Device to be executed
     * @param dimensions
     *            Select number of dimensions of the kernel (1D, 2D or 3D)
     * @return {@link TornadoAPI}
     */
    TornadoAPI prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions);

    /**
     * Add a pre-built OpenCL task into a task-schedule with atomics region
     * 
     * @param id
     *            Task-id
     * @param entryPoint
     *            Kernel's name of the entry point
     * @param filename
     *            Input OpenCL C Kernel
     * @param args
     *            Arguments to the method that the kernel represents.
     * @param accesses
     *            Array of access of each parameter to the kernel
     * @param device
     *            Device in which the OpenCL C code will be executed.
     * @param dimensions
     *            Select the dimension of the OpenCL kernel (1D, 2D or 3D)
     * @param atomics
     *            Atomics region.
     * @return {@link TornadoAPI}
     * 
     */
    TornadoAPI prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions, int[] atomics);

    /**
     * Obtains the task-schedule name that was assigned.
     * 
     * @return {@link String}
     */
    String getTaskScheduleName();

    /**
     * 
     * @param task
     *            {@link SchedulableTask}
     * @return {@link TornadoAPI}
     */
    TornadoAPI task(SchedulableTask task);

    TornadoAPI mapAllTo(TornadoDevice device);

    /**
     * Open a stream channel between the host and the target device.
     * 
     * @param objects
     *            list of input objects to be streamed.
     * @return link to the {@TornadoAPI} to allow function composition.
     */
    TornadoAPI streamIn(Object... objects);

    TornadoAPI forceCopyIn(Object... objects);

    /**
     * Open a stream channel between the device and the host.
     * 
     * @param objects
     *            list of input objects to be streamed.
     * @return link to the {@TornadoAPI} to allow function composition.
     */
    TornadoAPI streamOut(Object... objects);

    /**
     * Internal call to run the task-schedule
     * 
     * @return {@link TornadoAPI}
     */
    TornadoAPI schedule();

    /**
     * It enables batch processing on the target device.
     * 
     * @param batchSize
     *            size of the batch represented as a string. For example "512MB",
     *            "1GB". If the batchSize is <= 0 the whole array is computed
     *            without splitting in smaller batches.
     * @return link to the {@TornadoAPI} to allow function composition.
     */
    TornadoAPI batch(String batchSize);

    /**
     * Execute the task-schedule
     */
    void execute();

    void execute(GridScheduler gridScheduler);

    /**
     * Run with dynamic reconfiguration with an input policy
     * 
     * @param policy
     *            Input policy, See {@link Policy}
     */
    void executeWithProfiler(Policy policy);

    /**
     * Run with dynamic reconfiguration with an input policy. All combinations run
     * in sequential.
     *
     * @param policy
     *            Input policy, See {@link Policy}
     */
    void executeWithProfilerSequential(Policy policy);

    /**
     * Run with dynamic reconfiguration with an input policy. All combinations run
     * in sequential. It uses an internal table based on history to predict the
     * device to run.
     *
     * @param policy
     *            Input policy, See {@link Policy}
     */
    void executeWithProfilerSequentialGlobal(Policy policy);

    /**
     * It performs JIT compilation without running the task-schedule
     */
    void warmup();

    void dumpEvents();

    void dumpTimes();

    void dumpProfiles();

    void clearProfiles();

    /**
     * Locks this object on the device memory. If a {@link TaskSchedule} is executed multiple times, then
     * this object will be copied in only for the first execution.
     */
    TornadoAPI lockObjectInMemory(Object object);

    TornadoAPI lockObjectsInMemory(Object... objects);

    /**
     * Unlocks this object from the device memory. The object must have been previously locked in order to unlock it.
     * Once the object has been unlocked, it will be copied in on every subsequent execution of the {@link TaskSchedule}.
     */
    TornadoAPI unlockObjectFromMemory(Object object);

    TornadoAPI unlockObjectsFromMemory(Object... objects);

    void syncObjects();

    void syncObject(Object object);

    void syncObjects(Object... objects);

    SchedulableTask getTask(String id);

    TornadoDevice getDevice();

    void setDevice(TornadoDevice device);

    TornadoDevice getDeviceForTask(String id);

    void waitOn();

    TaskSchedule useDefaultThreadScheduler(boolean use);

    void updateReference(Object oldRef, Object newRef);

    boolean isFinished();
}
