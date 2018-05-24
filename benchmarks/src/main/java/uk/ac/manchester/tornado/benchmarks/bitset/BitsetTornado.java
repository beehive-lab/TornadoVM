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

package uk.ac.manchester.tornado.benchmarks.bitset;

import java.util.*;

import org.apache.lucene.util.*;

import uk.ac.manchester.tornado.benchmarks.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class BitsetTornado extends BenchmarkDriver {
    private int numWords;
    private TaskSchedule graph;

    public BitsetTornado(int size, int iterations) {
        super(iterations);
        this.numWords = size;
    }

    @Override
    public void setUp() {

        final Random rand = new Random(7);
        final long[] aBits = new long[numWords];
        final long[] bBits = new long[numWords];
        for (int i = 0; i < aBits.length; i++) {
            aBits[i] = rand.nextLong();
            bBits[i] = rand.nextLong();
        }

        final LongBitSet a = new LongBitSet(aBits, numWords * 8);
        final LongBitSet b = new LongBitSet(bBits, numWords * 8);

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::intersectionCount, numWords, a, b);
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void code() {
        graph.execute();
    }
}
