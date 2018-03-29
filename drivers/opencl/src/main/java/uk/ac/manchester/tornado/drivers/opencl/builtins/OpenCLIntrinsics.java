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
package uk.ac.manchester.tornado.drivers.opencl.builtins;

import uk.ac.manchester.tornado.api.ReductionOp;

public class OpenCLIntrinsics {

    public static int get_global_id(int value) {
        return 0;
    }

    public static int get_local_id(int value) {
        return 0;
    }

    public static int get_global_size(int value) {
        return 0;
    }

    public static int get_local_size(int value) {
        return 0;
    }

    public static int get_group_id(int value) {
        return 0;
    }

    public static int get_group_size(int value) {
        return 0;
    }

    /**
     * <p>
     * <code> 
     *  barrier(CLK_LOCAL_MEM_FENCE);
     * </code>
     * </p>
     */
    public static void localBarrier() {

    }

    /**
     * <p>
     * <code> 
     *  barrier(CLK_GLOBAL_MEM_FENCE);
     * </code>
     * </p>
     */
    public static void globalBarrier() {

    }

    public static void createLocalMemory(int[] array, int size) {

    }

    public static <T1, T2, R> R op(ReductionOp op, T1 x, T2 y) {
        return null;
    }

}
