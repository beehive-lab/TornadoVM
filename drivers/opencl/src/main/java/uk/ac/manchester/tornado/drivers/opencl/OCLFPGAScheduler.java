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
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLFPGAScheduler extends OCLKernelScheduler {

    private static final int LOCAL_WORK_SIZE = 64;
    private static final int WARP = 32;

    public OCLFPGAScheduler(final OCLDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateGlobalWork(final TaskMetaData meta) {
        final long[] globalWork = meta.getGlobalWork();

        for (int i = 0; i < meta.getDims(); i++) {
            long value = (long) (meta.getDomain().get(i).cardinality());
            if (value % WARP != 0) {
                value = ((value / WARP) + 1) * WARP;
            }
            globalWork[i] = value;
        }
    }

    @Override
    public void calculateLocalWork(final TaskMetaData meta) {
        final long[] localWork = meta.getLocalWork();
        switch (meta.getDims()) {
            case 3:
                localWork[2] = 1;
                localWork[1] = LOCAL_WORK_SIZE;
                localWork[0] = LOCAL_WORK_SIZE;

            case 2:
                localWork[1] = LOCAL_WORK_SIZE;
                localWork[0] = LOCAL_WORK_SIZE;
                break;
            case 1:
                localWork[0] = LOCAL_WORK_SIZE;
                break;
            default:
                break;
        }
    }
}
