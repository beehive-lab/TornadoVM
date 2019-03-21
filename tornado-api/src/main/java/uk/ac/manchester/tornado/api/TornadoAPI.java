/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
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
     * @return {@link @TornadoAPI}
     */
    TornadoAPI addTask(TaskPackage taskPackage);

    /**
     * 
     * @param id
     * @param code
     * @param arg
     * @return {@}
     */
    <T1> TornadoAPI task(String id, Task1<T1> code, T1 arg);

    /**
     * 
     * @param id
     * @param code
     * @param arg1
     * @param arg2
     * @return
     */
    <T1, T2> TornadoAPI task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2);

    <T1, T2, T3> TornadoAPI task(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3);

    <T1, T2, T3, T4> TornadoAPI task(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4);

    <T1, T2, T3, T4, T5> TornadoAPI task(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);

    <T1, T2, T3, T4, T5, T6> TornadoAPI task(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);

    <T1, T2, T3, T4, T5, T6, T7> TornadoAPI task(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);

    <T1, T2, T3, T4, T5, T6, T7, T8> TornadoAPI task(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);

    <T1, T2, T3, T4, T5, T6, T7, T8, T9> TornadoAPI task(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9);

    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TornadoAPI task(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9, T10 arg10);

    <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TornadoAPI task(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);

    /**
     * Add a prebuilt OpenCL task into a task-schedule
     * 
     * @param id
     * @param entryPoint
     * @param filename
     * @param args
     * @param accesses
     * @param device
     * @param dimensions
     * @return {@link TornadoAPI}
     */
    TornadoAPI prebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions);

    /**
     * Obtains the task-schedule name that was assigned.
     * 
     * @return {@link String}
     */
    String getTaskScheduleName();

    /**
     * 
     * @param task
     * @return
     */
    TornadoAPI task(SchedulableTask task);

    TornadoAPI mapAllTo(TornadoDevice device);

    TornadoAPI streamIn(Object... objects);

    TornadoAPI streamOut(Object... objects);

    TornadoAPI schedule();

    TornadoAPI batch(String batchSize);

    void execute();

    void executeWithProfiler(Policy policy);

    void executeWithProfilerSequential(Policy policy);

    void executeWithProfilerSequentialGlobal(Policy policy);

    void warmup();

    long getReturnValue(String id);

    void dumpEvents();

    void dumpTimes();

    void dumpProfiles();

    void clearProfiles();

    void syncObjects();

    void syncObject(Object object);

    void syncObjects(Object... objects);

    SchedulableTask getTask(String id);

    TornadoDevice getDevice();

    void setDevice(TornadoDevice device);

    TornadoDevice getDeviceForTask(String id);

    void waitOn();

}
