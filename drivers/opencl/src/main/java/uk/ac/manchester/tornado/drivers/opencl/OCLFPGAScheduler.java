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

    public OCLFPGAScheduler(final OCLDeviceContext context) {
        super(context);
        OCLDevice device = context.getDevice();

    }

    @Override
    public void calculateGlobalWork(final TaskMetaData meta) {
        final long[] globalWork = meta.getGlobalWork();

        for (int i = 0; i < meta.getDims(); i++) {
            long value = (long) (meta.getDomain().get(i).cardinality());
            // adjust for irregular problem sizes
            if (value % 32 != 0) {
                value = ((value / 32) + 1) * 32;
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
                localWork[1] = 16;
                localWork[0] = 16;

            case 2:
                localWork[1] = 16;
                localWork[0] = 16;

                break;
            case 1:
                localWork[0] = 64;
                break;
            default:
                break;
        }
    }

}
