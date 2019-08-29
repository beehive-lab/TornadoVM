/*
 * Copyright (c) 2019, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples.reductions;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

import java.util.Random;
import java.util.stream.IntStream;

public class ReduceFilter {

    private static final int SIZE = 8192;

    private static void tornadoRemoveOutliers(final double[] values, @Reduce double[] result) {
        final double sqrt = Math.sqrt(12.2321 / values.length);
        final double min = result[0] - (2 * sqrt);
        final double max = result[0] + (2 * sqrt);

        // Reduce with filter
        for (@Parallel int i = 0; i < values.length; i++) {
            if (values[i] > max || values[i] < min) {
                result[0]++;
            }
        }
    }

    private void testRemoveOutliers() {
        double[] input = new double[SIZE];

        IntStream.range(0, SIZE).forEach(idx -> input[idx] = 2.0);

        double[] result = new double[1];

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", ReduceFilter::tornadoRemoveOutliers, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on
    }

    public static void main(String[] args) {
        new ReduceFilter().testRemoveOutliers();
    }

}
