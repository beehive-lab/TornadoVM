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
package uk.ac.manchester.tornado.benchmarks;

import java.util.function.Consumer;

public class EventsSummary {
	private final double total;
	private final double min;
	private final double max;
	private final double mean;
	private final double stdDev;
	private final long count;
	
	protected EventsSummary(long count,double total,double min, double max,double mean, double stdDev){
		this.count = count;
		this.total = total;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.stdDev = stdDev;
	}
	
	public void apply(final Consumer<EventsSummary> function){
		function.accept(this);
	}

	public double getMean() {
		return mean;
	}

	public double getStdDev() {
		return stdDev;
	}

	public long getCount() {
		return count;
	}
	
	public String toString(){
		return String
		.format("events=%8d, total=%6f, min=%6f, max=%6f, mean=%6f, std. dev=%6f",
				count, total, min, max, mean, stdDev);
	}
}
