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
 * Authors: James Clarkson
 *
 */
package tornado.common;

import java.util.Arrays;

public class Statistics {
	double[]	data;
	double		size;

	public Statistics(final double[] data) {
		this.data = data;
		size = data.length;
	}

	public double getMean() {
		double sum = 0.0;
		for (final double a : data)
			sum += a;
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
		for (final double a : data)
			temp += (mean - a) * (mean - a);
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
