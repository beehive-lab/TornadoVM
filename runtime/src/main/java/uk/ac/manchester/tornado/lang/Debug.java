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
package uk.ac.manchester.tornado.lang;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class Debug {

    /**
     * prints a message from the zeroth thread
     *
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id, 0, 0]
     *
     * @param id   selected thread id
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id0, id1, 0]
     *
     * @param id0  selected thread id
     * @param id1  selected thread id
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id0, int id1, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id0, id1, id2]
     *
     * @param id0  selected thread id
     * @param id1  selected thread id
     * @param id2  selected thread id
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id0, int id1, int id2, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * conditionally prints a message from any thread where cond evaluatest to
     * true
     *
     * @param cond condition to evaluate
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void printf(boolean cond, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from all threads
     *
     * @param msg  format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void printf(String msg, Object... args) {
        shouldNotReachHere();
    }

}
