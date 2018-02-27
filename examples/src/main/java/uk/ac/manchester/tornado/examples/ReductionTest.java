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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class ReductionTest {

    public final int[] data;
    public int result;

    public void sum() {
        int sum = 0;
        for (@Parallel int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        result = sum;
    }

    public ReductionTest(int[] data) {
        this.data = data;
    }

    public static void main(String[] args) {
        final int[] data = new int[614400];

        Arrays.fill(data, 1);

        ReductionTest rt = new ReductionTest(data);

        new TaskSchedule("s0")
                .task("t0", ReductionTest::sum, rt)
                .streamOut(rt)
                .execute();

        int sum = 0;
        for (int value : data) {
            sum += value;
        }

        System.out.printf("result: %d == %d\n", rt.result, sum);

    }

}
