/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.examples.utils;

import java.util.Arrays;
import java.util.LongSummaryStatistics;

public class Utils {
    public static void computeStatistics(long[] totalTimes) {
        LongSummaryStatistics longSummaryStatistics = Arrays.stream(Arrays.stream(totalTimes).toArray()).summaryStatistics();
        double average = longSummaryStatistics.getAverage();
        double count = longSummaryStatistics.getCount();

        double[] variance = new double[totalTimes.length];
        for (int i = 0; i < variance.length; i++) {
            variance[i] = Math.pow((totalTimes[i] - average), 2);
        }
        double varianceScalar = Arrays.stream(variance).sum() / count;
        double std = Math.sqrt(varianceScalar);

        System.out.println("Min     : " + longSummaryStatistics.getMin());
        System.out.println("Max     : " + longSummaryStatistics.getMax());
        System.out.println("Average : " + longSummaryStatistics.getAverage());
        System.out.println("Variance: " + varianceScalar);
        System.out.println("STD     : " + std);
    }
}
