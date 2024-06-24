/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.bitsets;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.apache.lucene.util.LongBitSet;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test accelerating the Lucene library.
 *
 * How to test?
 *
 * <code>
 * tornado-test -V --fast uk.ac.manchester.tornado.unittests.bitsets.BitSetTests
 * </code>
 */
public class BitSetTests extends TornadoTestBase {

    public static void intersectionCount(int numWords, LongBitSet a, LongBitSet b, LongArray result) {
        final long[] aBits = a.getBits();
        final long[] bBits = b.getBits();
        for (@Parallel int i = 0; i < numWords; i++) {
            result.set(i, Long.bitCount(aBits[i] & bBits[i]));
        }
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {

        final int numWords = 8192;
        final Random rand = new Random(7);
        final long[] aBits = new long[numWords];
        final long[] bBits = new long[numWords];

        final LongBitSet a = new LongBitSet(aBits, numWords * 8);
        final LongBitSet b = new LongBitSet(bBits, numWords * 8);
        LongArray result = new LongArray(numWords);
        LongArray seq = new LongArray(numWords);

        for (int i = 0; i < aBits.length; i++) {
            aBits[i] = rand.nextLong();
            bBits[i] = rand.nextLong();
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", BitSetTests::intersectionCount, numWords, a, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        intersectionCount(numWords, a, b, seq);

        for (int i = 0; i < numWords; i++) {
            assertEquals(seq.get(i), result.get(i), 0.1f);
        }
    }

}
