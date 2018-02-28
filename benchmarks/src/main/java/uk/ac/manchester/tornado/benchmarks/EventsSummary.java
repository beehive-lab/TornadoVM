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
