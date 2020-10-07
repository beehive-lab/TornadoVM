/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

public class TornadoVMContext implements ExecutionContext {

    public final Integer threadIdx = 0; // get_global_id(0)
    public final Integer threadIdy = 0; // get_global_id(1)
    public final Integer threadIdz = 0; // get_global_id(2)
    public final Integer groupIdx = 0; // get_group_id(0)
    public final Integer groupIdy = 0; // get_group_id(1)
    public final Integer groupIdz = 0; // get_group_id(2)

    public final Integer localIdx = 0; // get_local_id(0);
    public final Integer localIdy = 0; // get_local_id(0);
    public final Integer localIdz = 0; // get_local_id(0);

    private WorkerGrid grid;

    public TornadoVMContext(WorkerGrid grid) {
        this.grid = grid;
    }

    // XXX Remove this
    @Override
    public WorkerGrid getGrid() {
        return this.grid;
    }

    @Override
    public void localBarrier() {
    }

    @Override
    public void globalBarrier() {

    }

    @Override
    public int[] allocateIntLocalArray(int size) {
        return new int[size];
    }

    @Override
    public long[] allocateLongLocalArray(int size) {
        return new long[size];
    }

    @Override
    public float[] allocateFloatLocalArray(int size) {
        return new float[size];
    }

    @Override
    public double[] allocateDoubleLocalArray(int size) {
        return new double[size];
    }

    @Override
    public void launch(FunctionalInterface f, WorkerGrid grid) {

    }

    public int getX() {
        return threadIdx;
    }

    public int getY() {
        return threadIdy;
    }

    public int getZ() {
        return threadIdz;
    }

    public int getLocalGroupSize(int dim) {
        return (int) grid.getLocalWork()[dim];
    }

    public int getGlobalGroupSize(int dim) {
        return (int) grid.getGlobalWork()[dim];
    }
}