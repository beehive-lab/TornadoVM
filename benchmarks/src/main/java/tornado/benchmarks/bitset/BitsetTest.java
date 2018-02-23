/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.benchmarks.bitset;

import java.util.Random;
import org.apache.lucene.util.LongBitSet;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class BitsetTest {

    public static final int intersectionCount(int numWords, LongBitSet a, LongBitSet b) {
        final long[] aBits = a.getBits();
        final long[] bBits = b.getBits();
        int sum = 0;
        for (@Parallel int i = 0; i < numWords; i++) {
            sum += Long.bitCount(aBits[i] & bBits[i]);
        }
        return sum;
    }

    public static final void main(String[] args) {

        final int numWords = Integer.parseInt(args[0]);

        final Random rand = new Random(7);
        final long[] aBits = new long[numWords];
        final long[] bBits = new long[numWords];
        for (int i = 0; i < aBits.length; i++) {
            aBits[i] = rand.nextLong();
            bBits[i] = rand.nextLong();
        }

        final LongBitSet a = new LongBitSet(aBits, numWords * 8);
        final LongBitSet b = new LongBitSet(bBits, numWords * 8);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", BitsetTest::intersectionCount, numWords, a, b);

        s0.execute();

        final long value = s0.getReturnValue("t0");
        System.out.printf("value = 0x%x, %d\n", value, value);

        final long ref = intersectionCount(numWords, a, b);
        System.out.printf("ref   = 0x%x, %d\n", ref, ref);

    }

}
