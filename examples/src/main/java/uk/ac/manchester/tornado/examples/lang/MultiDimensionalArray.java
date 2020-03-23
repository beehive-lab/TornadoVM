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

package uk.ac.manchester.tornado.examples.lang;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MultiDimensionalArray {

    public static void fill(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            Arrays.fill(values[i], i);
        }
    }

    public static final void main(String[] args) {

        int n = 8;
        int m = 8;
        int[][] values = new int[n][m];

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", MultiDimensionalArray::fill, values).streamOut(new Object[] { values });

        s0.warmup();

        s0.execute();

        for (int i = 0; i < values.length; i++) {
            System.out.printf("%d| %s\n", i, Arrays.toString(values[i]));
        }

    }

}
