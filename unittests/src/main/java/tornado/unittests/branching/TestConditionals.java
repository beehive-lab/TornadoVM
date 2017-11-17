/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
 * Authors: Michalis Papadimitriou
 */

package tornado.unittests.branching;

import org.junit.Test;
import tornado.runtime.api.TaskSchedule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestConditionals {

    public static void ifStatement(int[] a) {

        if (a[0] > 1) {
            a[0] = 10;
        }
    }

    @Test
    public void testIfStatement() {

        final int size = 10;

        int[] a = new int[size];

        Arrays.fill(a, 5);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::ifStatement, a)
                .streamOut(a)
                .execute();
        //formatter:on

        assertEquals(10, a[0]);
    }

    public static void ifElseStatement(int[] a) {

        if (a[0] == 1) {
            a[0] = 5;
        } else {
            a[0] = 10;
        }
    }

    @Test
    public void testIfElseStatement() {

        final int  size = 10;

        int[] a = new int[size];

        Arrays.fill(a, 5);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::ifElseStatement, a)
                .streamOut(a)
                .execute();
        //formatter:on

        assertEquals(10, a[0]);
    }
}
