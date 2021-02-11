/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.common.GridInfo;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;

import java.util.Arrays;

public class PTXGridInfo implements GridInfo {
    public final PTXModule ptxModule;
    public final long[] localWork;

    public PTXGridInfo(PTXModule ptxModule, long[] localWork) {
        this.ptxModule = ptxModule;
        this.localWork = localWork;
    }

    @Override
    public void checkGridDimensions() {
        int maxWorkGroupSize = ptxModule.getMaxThreadBlocks();
        long totalThreads = Arrays.stream(localWork).reduce(1, (a, b) -> a * b);

        if (totalThreads > maxWorkGroupSize) {
            throw new TornadoBailoutRuntimeException(
                    "The total number of threads per block dimension exceed the hardware capacity. The product of x, y and z in setLocalWork(x, y, z) should be less than or equal to "
                            + maxWorkGroupSize + ". In this case it was: " + localWork[0] + " * " + localWork[1] + " * " + localWork[2] + " = " + totalThreads + ".");
        }
    }
}
