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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.api;

public final class TornadoFunctions {

    @FunctionalInterface
    public interface Task1<T1> {

        public void apply(T1 arg1);
    }

    @FunctionalInterface
    public interface Task2<T1, T2> {

        public void apply(T1 arg1, T2 arg2);
    }

    @FunctionalInterface
    public interface Task3<T1, T2, T3> {

        public void apply(T1 arg1, T2 arg2, T3 arg3);
    }

    @FunctionalInterface
    public interface Task4<T1, T2, T3, T4> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    @FunctionalInterface
    public interface Task5<T1, T2, T3, T4, T5> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    @FunctionalInterface
    public interface Task6<T1, T2, T3, T4, T5, T6> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6);
    }

    @FunctionalInterface
    public interface Task7<T1, T2, T3, T4, T5, T6, T7> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7);
    }

    @FunctionalInterface
    public interface Task8<T1, T2, T3, T4, T5, T6, T7, T8> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8);
    }

    @FunctionalInterface
    public interface Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9);
    }

    @FunctionalInterface
    public interface Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10);
    }

    @FunctionalInterface
    public interface Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> {

        public void apply(T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15);
    }
}
