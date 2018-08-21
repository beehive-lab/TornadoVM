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

import java.util.ArrayList;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.ProfiledAction;

public class EventList<T extends ProfiledAction> extends ArrayList<T> {

	private static final long serialVersionUID = 7127015308775832135L;

	public EventList(int size) {
		super(size);
	}
	
	public EventList(){
		super();
	}

	public final double getTotalExecutionTime() {
		double total = 0.0;

		for(ProfiledAction e : this){
			total += e.getExecutionTime();
		}

		return total;
	}
	
	public final double getMinExecutionTime(){
		double result = Double.MAX_VALUE;
		for(ProfiledAction e: this){
			result = Math.min(result, e.getExecutionTime());
		}
		return result;
	}
	
	public final double getMaxExecutionTime(){
		double result = Double.MIN_VALUE;
		for(ProfiledAction e: this){
			result = Math.max(result, e.getExecutionTime());
		}
		return result;
	}
	
	public final double getMeanExecutionTime() {
		return getTotalExecutionTime() / size();
	}

	public final double getExecutionStdDev() {
		return Math.sqrt(getExecutionVariance());
	}

	public double getExecutionVariance() {
		final double mean = getMeanExecutionTime();
		double temp = 0;
		for(final ProfiledAction e : this){
			final double value = e.getExecutionTime();
			temp += (mean - value) * (mean - value);
		}
		return temp / size();
	}
	
	public void apply(final Consumer<T> function){
		for(final T e: this){
			function.accept(e);
		}
	}

	public final EventsSummary summeriseEvents() {
		return new EventsSummary(size(),getTotalExecutionTime(),getMinExecutionTime(),getMaxExecutionTime(),getMeanExecutionTime(),getExecutionStdDev());	
	}
	
	public T getLast(){
		return get(size() - 1);
	}
}
