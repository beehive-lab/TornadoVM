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

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestBatches extends TornadoTestBase {

    public static void compute(float[] array) {
        for (@Parallel int i = 0; i < array.length; i++) {
            array[i] = array[i];
        }
    }

    public static void compute(float[] arrayA, float[] arrayB) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayB[i] = arrayA[i] + 100;
        }
    }

    public static void compute(float[] arrayA, float[] arrayB, float[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = arrayA[i] + arrayB[i];
        }
    }

    @Test
    public void test100MB() {

        // Fill 800MB of float array
        int size = 200000000;
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);

        TaskSchedule ts = new TaskSchedule("s0");

        // @formatter:off
        ts.batch("100MB")   // Slots of 100 MB
          .task("t0", TestBatches::compute, arrayA, arrayB)
          .streamOut((Object) arrayB)
          .execute();
        // @formatter:on

        for (int i = 0; i < arrayB.length; i++) {
            assertEquals(arrayA[i] + 100, arrayB[i], 0.1f);
        }
    }

    @Test
    public void test300MB() {

        // Fill 1.0GB
        int size = 250000000;
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);

        TaskSchedule ts = new TaskSchedule("s0");

        // @formatter:off
        ts.batch("300MB")   // Slots of 300 MB
          .task("t0", TestBatches::compute, arrayA, arrayB)
          .streamOut((Object) arrayB)
          .execute();
        // @formatter:on

        for (int i = 0; i < arrayB.length; i++) {
            assertEquals(arrayA[i] + 100, arrayB[i], 0.1f);
        }
    }

    @Test
    public void test512MB() {

        // Fill 800MB
        int size = 200000000;
        float[] arrayA = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);

        TaskSchedule ts = new TaskSchedule("s0");

        // @formatter:off
        ts.batch("512MB")   // Slots of 512 MB
          .task("t0", TestBatches::compute, arrayA)
          .streamOut((Object) arrayA)
          .execute();
        // @formatter:on

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(i, arrayA[i], 0.1f);
        }
    }

    @Test
    public void test50MB() {

        // Fill 80MB of input Array
        int size = 20000000;
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];
        float[] arrayC = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = idx;
            arrayB[idx] = idx;
        });

        TaskSchedule ts = new TaskSchedule("s0");

        // @formatter:off
        ts.batch("50MB")   // Process Slots of 50 MB
          .task("t0", TestBatches::compute, arrayA, arrayB, arrayC)
          .streamOut((Object) arrayC)
          .execute();
        // @formatter:on

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i], 0.1f);
        }
    }

}
