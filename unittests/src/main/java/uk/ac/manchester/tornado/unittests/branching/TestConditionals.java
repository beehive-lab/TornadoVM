/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.unittests.branching;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
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
    
    
}
