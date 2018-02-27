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

import java.util.ArrayList;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.ProfiledAction;

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
