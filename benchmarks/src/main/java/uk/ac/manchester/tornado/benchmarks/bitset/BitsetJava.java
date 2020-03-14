/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class BitsetJava extends BenchmarkDriver {

    private int numWords;
    private TaskSchedule graph;
    private LongBitSet a;
    private LongBitSet b;
    private long[] result;

    public BitsetJava(int size, int iterations) {
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

        a = new LongBitSet(aBits, numWords * 8);
        b = new LongBitSet(bBits, numWords * 8);
        result = new long[numWords];

    }

    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void code() {
        ComputeKernels.intersectionCount(numWords, a, b, result);
    }
}
