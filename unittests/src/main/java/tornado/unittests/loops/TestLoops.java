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
 *
 */
package tornado.unittests.loops;

import org.junit.Ignore;
import org.junit.Test;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestLoops {

    public static void forLoopOneD(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 10;
        }
    }

    @Test
    public void testForLoopOneD() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::forLoopOneD, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++){
            assertEquals(10, a[i]);
        }
    }

    public static void reverseLoop(int[] a) {
        for (@Parallel int i = a.length - 1; i >= 0; i--){
            a[i] =  10;
            //Debug.printf("hello\n");
        }
    }

    @Test
    public void testReverseOneDLoop() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0",TestLoops::reverseLoop, a)
                .streamOut(a)
                .execute();
        //formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(10, a[i]);
        }
    }

    public static void steppedLoop(int[] a, int size) {
        for (@Parallel int i = 0; i < size - 1; i += 2){
            a[i] = 10;
            a[i + 1] = 10;
        }
    }

    @Test
    public void testStepLoop() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0",TestLoops::steppedLoop, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++){
            assertEquals(10, a[i]);
        }
    }

    public static void conditionalInLoop(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++){
            if (i == 4) {
                a[i] = 4;
            } else {
                a[i] = 10;
            }
        }
    }

    @Test
    public void testIfInsideForLoop() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::conditionalInLoop, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            if (i == 4) {
                assertEquals(4, a[i]);
            } else {
                assertEquals(10, a[i]);
            }
        }
    }

    @Ignore
    public void testIfElseElseInLoop() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,0);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::forLoopOneD, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++){
            assertEquals(a[i], 1, 0);
        }
    }

    public static void twoDLoop(int[][] a) {
        for (@Parallel int i = 0; i < a.length; i++){
            for(int j = 0; j < a[i].length; j++) {
                a[i][j] = 10;
            }
        }
    }

    @Ignore
    public void testTwoDLoopTwoDArray() {
        final int size = 10;

        int[][] a = new int[size][size];

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::twoDLoop, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(10, a[i][j]);
            }
        }
    }

    public static void nestedForLoopOneDArray(int[] a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i * size + j] = 10;
            }
        }
    }

    @Test
    public void testNestedForLoopOneDArray() {
        final int size = 10;

        int [] a = new int[size * size];
        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::nestedForLoopOneDArray, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(10, a[i * size + j]);
            }
        }
    }

    public static void nestedForLoopTwoDArray(int[][] a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i][j] = 10;
            }
        }
    }

    @Ignore
    public void testNestedForLoopTwoDArray() {
        final int size = 10;

        int [][] a = new int[size][size];

        for (int i = 0; i < size; i++){
            Arrays.fill(a[i], 1);
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::nestedForLoopTwoDArray, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(10, a[i][j]);
            }
        }
    }

    public static void controlFlowBreak(int[] a,int size) {
        for (int i = 0; i < a.length; i++) {
            if (i == 4) {
                a[i] = 4;
                break;
            }
        }
    }

    @Test
    public void testLoopControlFlowBreak() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::controlFlowBreak, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            if (i == 4) {
                assertEquals(4, a[i]);
            } else {
                assertEquals(10, a[i]);
            }
        }
    }

    public static void controlFlowContinue(int[] a) {
        for (int i = 0; i < a.length; i++) {
            if (i == 4) {
                continue;
            }
            a[i] = 10;
        }
    }

    @Test
    public void testLoopControlFlowContinue() {
        final int size = 10;

        int [] a = new int[size];

        Arrays.fill(a,1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestLoops::controlFlowContinue, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            if (i == 4) {
                assertEquals(1, a[i]);
            } else {
                assertEquals(10, a[i]);
            }
        }
    }
}
