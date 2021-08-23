/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.fields;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestFields extends TornadoTestBase {

    private static class Foo {
        final int[] output;
        final int[] a;
        final int[] b;

        public Foo(int elements) {
            output = new int[elements];
            a = new int[elements];
            b = new int[elements];
        }

        public void initRandom() {
            Random r = new Random();
            IntStream.range(0, a.length).forEach(idx -> {
                a[idx] = r.nextInt(100);
                b[idx] = r.nextInt(100);
            });
        }

        public void computeInit() {
            for (@Parallel int i = 0; i < output.length; i++) {
                output[i] = 100;
            }
        }

        public void computeAdd() {
            for (@Parallel int i = 0; i < output.length; i++) {
                output[i] = a[i] + b[i];
            }
        }
    }

    private static class Bar {
        final int[] output;
        int initValue;

        public Bar(int elements, int initValue) {
            output = new int[elements];
            this.initValue = initValue;
        }

        public void computeInit() {
            for (@Parallel int i = 0; i < output.length; i++) {
                output[i] = initValue;
            }
        }
    }

    @Test
    public void testFields01() {
        final int N = 1024;
        Foo foo = new Foo(N);

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", foo::computeInit).execute();
        s0.syncObject(foo.output);

        for (int i = 0; i < N; i++) {
            assertEquals(100, foo.output[i]);
        }
    }

    @Test
    public void testFields02() {
        final int N = 1024;
        Foo foo = new Foo(N);
        foo.initRandom();

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", foo::computeAdd).execute();
        s0.syncObject(foo.output);

        for (int i = 0; i < N; i++) {
            assertEquals(foo.a[i] + foo.b[i], foo.output[i]);
        }
    }

    @Test
    public void testFields03() {
        final int N = 1024;
        Bar bar = new Bar(N, 15);

        TaskSchedule s0 = new TaskSchedule("Bar");
        assertNotNull(s0);

        s0.task("init", bar::computeInit).execute();
        s0.syncObject(bar.output);

        for (int i = 0; i < N; i++) {
            assertEquals(15, bar.output[i]);
        }
    }

}
