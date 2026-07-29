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

public final class TornadoFunctions {

    @FunctionalInterface
    public interface Task {
        void apply();
    }

    @FunctionalInterface
    public interface Task1<T1> {
        void apply(T1 arg1);
    }

    @FunctionalInterface
    public interface Task2<T1, T2> {
        void apply(T1 arg1, T2 arg2);
    }

    @FunctionalInterface
    public interface Task3<T1, T2, T3> {
        void apply(T1 arg1, T2 arg2, T3 arg3);
    }

    @FunctionalInterface
    public interface Task4<T1, T2, T3, T4> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    @FunctionalInterface
    public interface Task5<T1, T2, T3, T4, T5> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    @FunctionalInterface
    public interface Task6<T1, T2, T3, T4, T5, T6> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);
    }

    @FunctionalInterface
    public interface Task7<T1, T2, T3, T4, T5, T6, T7> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);
    }

    @FunctionalInterface
    public interface Task8<T1, T2, T3, T4, T5, T6, T7, T8> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);
    }

    @FunctionalInterface
    public interface Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9);
    }

    @FunctionalInterface
    public interface Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10);
    }

    @FunctionalInterface
    public interface Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11);
    }

    @FunctionalInterface
    public interface Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12);
    }

    @FunctionalInterface
    public interface Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13);
    }

    @FunctionalInterface
    public interface Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14);
    }

    @FunctionalInterface
    public interface Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);
    }

    @FunctionalInterface
    public interface Task16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16);
    }

    @FunctionalInterface
    public interface Task17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17);
    }

    @FunctionalInterface
    public interface Task18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18);
    }

    @FunctionalInterface
    public interface Task19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18,
                T19 arg19);
    }

    @FunctionalInterface
    public interface Task20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> {
        void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18,
                T19 arg19, T20 arg20);
    }

    @FunctionalInterface
    public interface LibraryTask1<T1> {
        LibraryTaskDescriptor apply(T1 arg1);
    }

    @FunctionalInterface
    public interface LibraryTask2<T1, T2> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2);
    }

    @FunctionalInterface
    public interface LibraryTask3<T1, T2, T3> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3);
    }

    @FunctionalInterface
    public interface LibraryTask4<T1, T2, T3, T4> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    @FunctionalInterface
    public interface LibraryTask5<T1, T2, T3, T4, T5> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    @FunctionalInterface
    public interface LibraryTask6<T1, T2, T3, T4, T5, T6> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);
    }

    @FunctionalInterface
    public interface LibraryTask7<T1, T2, T3, T4, T5, T6, T7> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);
    }

    @FunctionalInterface
    public interface LibraryTask8<T1, T2, T3, T4, T5, T6, T7, T8> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);
    }

    @FunctionalInterface
    public interface LibraryTask9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9);
    }

    @FunctionalInterface
    public interface LibraryTask10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10);
    }

    @FunctionalInterface
    public interface LibraryTask11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11);
    }

    @FunctionalInterface
    public interface LibraryTask12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12);
    }

    @FunctionalInterface
    public interface LibraryTask13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13);
    }

    @FunctionalInterface
    public interface LibraryTask14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14);
    }

    @FunctionalInterface
    public interface LibraryTask15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);
    }

    @FunctionalInterface
    public interface LibraryTask16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16);
    }

    @FunctionalInterface
    public interface LibraryTask17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16,
                T17 arg17);
    }

    @FunctionalInterface
    public interface LibraryTask18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16,
                T17 arg17, T18 arg18);
    }

    @FunctionalInterface
    public interface LibraryTask19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16,
                T17 arg17, T18 arg18, T19 arg19);
    }

    @FunctionalInterface
    public interface LibraryTask20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> {
        LibraryTaskDescriptor apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16,
                T17 arg17, T18 arg18, T19 arg19, T20 arg20);
    }
}
