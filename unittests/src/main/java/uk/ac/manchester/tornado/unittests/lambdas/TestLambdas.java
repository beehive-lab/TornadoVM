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
package uk.ac.manchester.tornado.unittests.lambdas;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestLambdas extends TornadoTestBase {

    @Test
    public void testLambda01() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Random r = new Random();

        IntStream.range(0, a.length).forEach(i -> a[i] = r.nextInt(100));

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", (x, y) -> 
            {
              for (@Parallel int i = 0; i < x.length; i++) {
                  x[i] = y[i] * y[i];
              }
            }, a, b)
            .streamOut(a)
            .execute();
        //@formatter:on

        for (int i = 0; i < b.length; i++) {
            assertEquals(b[i] * b[i], a[i]);
        }
    }

}
