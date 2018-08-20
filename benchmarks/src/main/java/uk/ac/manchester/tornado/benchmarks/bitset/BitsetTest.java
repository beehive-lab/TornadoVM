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
package uk.ac.manchester.tornado.benchmarks.bitset;

import java.util.*;

import org.apache.lucene.util.*;

import uk.ac.manchester.tornado.api.annotations.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class BitsetTest {

    public static final int intersectionCount(int numWords, LongBitSet a, LongBitSet b) {
        final long[] aBits = a.getBits();
        final long[] bBits = b.getBits();
        int sum = 0;
        for (@Parallel int i = 0; i < numWords; i++) {
            Long.bitCount(aBits[i] & bBits[i]);
        }
        return sum;
    }

    public static final void main(String[] args) {

        final int numWords = Integer.parseInt(args[0]);
        final int iterations = Integer.parseInt(args[1]);

        StringBuffer resultsIterations = new StringBuffer();

        final Random rand = new Random(7);
        final long[] aBits = new long[numWords];
        final long[] bBits = new long[numWords];
        for (int i = 0; i < aBits.length; i++) {
            aBits[i] = rand.nextLong();
            bBits[i] = rand.nextLong();
        }

        final LongBitSet a = new LongBitSet(aBits, numWords * 8);
        final LongBitSet b = new LongBitSet(bBits, numWords * 8);

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", BitsetTest::intersectionCount, numWords, a, b);

        s0.warmup();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            s0.execute();
            long end = System.nanoTime();
            resultsIterations.append("Execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        System.out.println(resultsIterations.toString());

        final long value = s0.getReturnValue("t0");
        System.out.printf("value = 0x%x, %d\n", value, value);

        final long ref = intersectionCount(numWords, a, b);
        System.out.printf("ref   = 0x%x, %d\n", ref, ref);

    }

}
