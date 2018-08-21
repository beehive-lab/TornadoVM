/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.api;

import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task9;

public class TaskPackage {

    private String id;
    private int taskType;
    private Object[] taskParameters;

    public <T1> TaskPackage(String id, Task1<T1> code, T1 arg) {
        this.id = id;
        this.taskType = 1;
        this.taskParameters = new Object[] { code, arg };
    }

    public <T1, T2> TaskPackage(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        this.id = id;
        this.taskType = 2;
        this.taskParameters = new Object[] { code, arg1, arg2 };
    }

    public <T1, T2, T3> TaskPackage(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        this.id = id;
        this.taskType = 3;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3 };

    }

    public <T1, T2, T3, T4> TaskPackage(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        this.id = id;
        this.taskType = 4;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4 };
    }

    public <T1, T2, T3, T4, T5> TaskPackage(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        this.id = id;
        this.taskType = 5;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5 };
    }

    public <T1, T2, T3, T4, T5, T6> TaskPackage(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        this.id = id;
        this.taskType = 6;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6 };
    }

    public <T1, T2, T3, T4, T5, T6, T7> TaskPackage(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        this.id = id;
        this.taskType = 7;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> TaskPackage(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        this.id = id;
        this.taskType = 8;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskPackage(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9) {
        this.id = id;
        this.taskType = 9;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskPackage(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8, T9 arg9, T10 arg10) {
        this.id = id;
        this.taskType = 10;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskPackage(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        this.id = id;
        this.taskType = 15;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15 };
    }

    public String getId() {
        return id;
    }

    public int getTaskType() {
        return taskType;
    }

    public Object[] getTaskParameters() {
        return taskParameters;
    }

    public static <T1> TaskPackage createPackage(String id, Task1<T1> code, T1 arg) {
        return new TaskPackage(id, code, arg);
    }

    public static <T1, T2> TaskPackage createPackage(String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        return new TaskPackage(id, code, arg1, arg2);
    }

    public static <T1, T2, T3> TaskPackage createPackage(String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        return new TaskPackage(id, code, arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> TaskPackage createPackage(String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> TaskPackage createPackage(String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> TaskPackage createPackage(String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> TaskPackage createPackage(String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> TaskPackage createPackage(String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7,
            T8 arg8) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> TaskPackage createPackage(String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7, T8 arg8, T9 arg9) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> TaskPackage createPackage(String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskPackage createPackage(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code,
            T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }
}
