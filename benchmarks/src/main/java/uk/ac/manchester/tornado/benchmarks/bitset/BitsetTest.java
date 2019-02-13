/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package uk.ac.manchester.tornado.benchmarks.bitset;

import java.util.Random;

import org.apache.lucene.util.LongBitSet;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

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
