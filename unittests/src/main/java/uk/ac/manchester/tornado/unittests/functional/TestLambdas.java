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

package uk.ac.manchester.tornado.unittests.functional;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestLambdas extends TornadoTestBase {

    @Test
    public void testVectorFunctionLambda() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = Math.random();
            b[i] = Math.random();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", (x, y, z) -> {	 
                // Computation in a lambda expression
                for (@Parallel int i = 0; i < z.length; i++) {
                    z[i] = x[i] + y[i];
                }    	 
            }, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorFunctionLambda02() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Random r = new Random();

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextDouble();
            b[i] = r.nextInt(1000);
        });

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", (x, y, z) -> {   
                // Computation in a lambda expression
                for (@Parallel int i = 0; i < z.length; i++) {
                    z[i] = x[i] * y[i];
                }        
            }, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] * b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorFunctionLambda03() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        int[] b = new int[numElements];
        double[] c = new double[numElements];

        Random r = new Random();

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextDouble();
            b[i] = r.nextInt(1000);
        });

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", (x, y, z) -> {
                    // Computation in a lambda expression
                    for (@Parallel int i = 0; i < z.length; i++) {
                        z[i] = x[i] * y[i];
                    }
                }, a, b, c)
                .streamOut(c)
                .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] * b[i], c[i], 0.001);
        }
    }

}
