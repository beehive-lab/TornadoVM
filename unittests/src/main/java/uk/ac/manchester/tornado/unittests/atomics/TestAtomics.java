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

package uk.ac.manchester.tornado.unittests.atomics;

import java.util.Arrays;

import org.junit.Ignore;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.type.annotations.Atomic;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAtomics extends TornadoTestBase {

    public static void atomic01(@Atomic int[] a, int sum) {
        for (@Parallel int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        a[0] = sum;
    }

    @Ignore
    public void testAtomic() {
        final int size = 10;

        int[] a = new int[size];
        int sum = 0;

        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestAtomics::atomic01, a, sum)
                .streamOut(a)
                .execute();
        //@formatter:on
    }

}
