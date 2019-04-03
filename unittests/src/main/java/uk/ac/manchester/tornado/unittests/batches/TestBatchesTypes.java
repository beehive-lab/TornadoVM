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

package uk.ac.manchester.tornado.unittests.batches;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class TestBatchesTypes {

    @Test
    public void test50MBShort() {

        // Fill 160MB of input Array
        int size = 80000000;
        short[] arrayA = new short[size];
        short[] arrayB = new short[size];
        short[] arrayC = new short[size];

        Random r = new Random();
        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = (short) r.nextInt(Short.MAX_VALUE / 2);
            arrayB[idx] = (short) r.nextInt(Short.MAX_VALUE / 2);
        });

        TaskSchedule ts = new TaskSchedule("s0");

        // @formatter:off
        ts.batch("50MB")   // Process Slots of 50 MB
          .task("t0", TestBatches::compute, arrayA, arrayB, arrayC)
          .streamOut((Object) arrayC)
          .execute();
        // @formatter:on

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i]);
        }
    }

}
