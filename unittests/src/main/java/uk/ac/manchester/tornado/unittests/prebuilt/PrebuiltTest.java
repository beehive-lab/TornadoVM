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
 */

package uk.ac.manchester.tornado.unittests.prebuilt;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.common.enums.Access;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class PrebuiltTest {

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Test
    public void testPrebuild01() {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // @formatter:off
        new TaskSchedule("s0")
            .prebuiltTask("t0", 
                        "add", 
                        "opencl/add.cl", 
                        new Object[] { a, b, c },
                        new Access[] { Access.READ, Access.READ, Access.WRITE }, 
                        OpenCL.defaultDevice(),
                        new int[] { numElements })
            .streamOut(c)
            .execute();
        // @formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

}
