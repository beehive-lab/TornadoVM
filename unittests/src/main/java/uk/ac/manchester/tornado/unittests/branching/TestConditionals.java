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

package uk.ac.manchester.tornado.unittests.branching;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestConditionals extends TornadoTestBase {

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
    
    
    public static void switchStatement(int[] a) {
        int value = a[0];
        switch(value) {
            case 10: a[0] = 5;
                break;
            case 20: a[0] = 10;
                break;
            default:
                 a[0] = 20;
        }
    }

    @Test
    public void testSwitch() {

        final int  size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::switchStatement, a)
                .streamOut(a)
                .execute();
        //formatter:on

        assertEquals(10, a[0]);
    }
    
    public static void switchStatement2(int[] a) {
        int value = a[0];
        switch(value) {
            case 10: a[0] = 5;
                break;
            case 20: a[0] = 10;
                break;
        }
    }

    @Test
    public void testSwitch2() {

        final int  size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::switchStatement2, a)
                .streamOut(a)
                .execute();
        //formatter:on

        assertEquals(10, a[0]);
    }
    
    public static void switchStatement3(int[] a) {
        for (int i = 0; i < a.length; i++) {
            int value = a[i];
            switch(value) {
                case 10: a[i] = 5;
                    break;
                case 20: a[i] = 10;
                    break;
            }
        }
    }

    @Test
    public void testSwitch3() {

        final int  size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::switchStatement3, a)
                .streamOut(a)
                .execute();
        //formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(10, a[i]);   
        }
    }
    
    public static void switchStatement4(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            int value = a[i];
            switch(value) {
                case 10: a[i] = 5;
                    break;
                case 20: a[i] = 10;
                    break;
            }
        }
    }

    @Test
    public void testSwitch4() {

        final int  size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::switchStatement4, a)
                .streamOut(a)
                .execute();
        //formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(10, a[i]);   
        }
    }

    public static void ternaryCondition(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (a[i] == 20) ? 10 : 5;
        }
    }

    @Test
    public void testTernaryCondition() {

        final int  size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::ternaryCondition, a)
                .streamOut(a)
                .execute();
        //formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(10, a[i]);
        }
    }

    public static void ternaryComplexCondition(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            for (int x = 0; x < a.length; x++) {
                    if (i == a.length - 128) {
                        a[x] = (a[x] == 20) ? a[x]+b[x] : 5;
                    }
            }
        }
    }

    @Test
    public void testComplexTernaryCondition() {

        final int  size = 8192;
        int[] a = new int[size];
        int[] b = new int[size];


        Arrays.fill(a, 20);
        Arrays.fill(b, 30);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestConditionals::ternaryComplexCondition, a, b)
                .streamOut(a)
                .execute();
        //formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(20, a[i]);
        }
    }
}
