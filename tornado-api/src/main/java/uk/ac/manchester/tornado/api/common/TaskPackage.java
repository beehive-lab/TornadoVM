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
package uk.ac.manchester.tornado.api.common;

import uk.ac.manchester.tornado.api.AccessorParameters;
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

public class TaskPackage {

    private final String id;
    private final int taskType;
    private final Object[] taskParameters;
    private long numThreadsToRun;

    private boolean isPrebuiltTask;

    public TaskPackage(String id, Task code) {
        this.id = id;
        this.taskType = 0;
        this.taskParameters = new Object[] { code };
    }

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

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskPackage(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        this.id = id;
        this.taskType = 11;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskPackage(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12) {
        this.id = id;
        this.taskType = 12;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskPackage(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13) {
        this.id = id;
        this.taskType = 13;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskPackage(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        this.id = id;
        this.taskType = 14;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14 };
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskPackage(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        this.id = id;
        this.taskType = 15;
        this.taskParameters = new Object[] { code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15 };
    }

    public static TaskPackage createPackage(String id, Task code) {
        return new TaskPackage(id, code);
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

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> TaskPackage createPackage(String id, Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> TaskPackage createPackage(String id, Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> TaskPackage createPackage(String id, Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> TaskPackage createPackage(String id, Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> TaskPackage createPackage(String id, Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code,
            T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        return new TaskPackage(id, code, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    public static PrebuiltTaskPackage createPrebuiltTask(String id, String entryPoint, String filename, AccessorParameters accessorParameters) {
        return new PrebuiltTaskPackage(id, entryPoint, filename, accessorParameters);
    }

    public String getId() {
        return id;
    }

    public int getTaskType() {
        return taskType;
    }

    public long getNumThreadsToRun() {
        return numThreadsToRun;
    }

    public void setNumThreadsToRun(long numThreads) {
        this.numThreadsToRun = numThreads;
    }

    /**
     * Get all parameters to the lambda expression. First parameter is reserved to the input code.
     *
     * @return an object array with all parameters.
     */
    public Object[] getTaskParameters() {
        return taskParameters;
    }

    public boolean isPrebuiltTask() {
        return isPrebuiltTask;
    }

}
