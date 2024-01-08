/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.util.Arrays;

public class Statistics {
    double[] data;
    double size;

    public Statistics(final double[] data) {
        this.data = data;
        size = data.length;
    }

    public double getMean() {
        double sum = 0.0;
        for (final double a : data) {
            sum += a;
        }
        return sum / size;
    }

    public double getStdDev() {
        return Math.sqrt(getVariance());
    }

    public double getSum() {
        double sum = 0.0;
        for (final double a : data) {
            sum += a;
        }
        return sum;
    }

    public double getVariance() {
        final double mean = getMean();
        double temp = 0;
        for (final double a : data) {
            temp += (mean - a) * (mean - a);
        }
        return temp / size;
    }

    public double median() {
        final double[] b = new double[data.length];
        System.arraycopy(data, 0, b, 0, b.length);
        Arrays.sort(b);

        if ((data.length % 2) == 0) {
            return (b[(b.length / 2) - 1] + b[b.length / 2]) / 2.0;
        } else {
            return b[b.length / 2];
        }
    }

}
