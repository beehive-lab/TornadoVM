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
package uk.ac.manchester.tornado.collections.types;

import static java.lang.String.format;

public class FloatingPointError {
	private final float averageUlp;
	private final float minUlp;
	private final float maxUlp;
	private final float stdDevUlp;
	private final int errors;
	
	public FloatingPointError(float average, float min, float max, float stdDev, int errors){
		this.averageUlp = average;
		this.minUlp = min;
		this.maxUlp = max;
		this.stdDevUlp = stdDev;
		this.errors = errors;
	}
	
	public FloatingPointError(float average, float min, float max, float stdDev){
		this(average,min,max,stdDev,-1);
	}
	
	public String toString(){
		return format("errors=%d, mean ulp=%f, std. dev =%f, min ulp=%f, max ulp=%f",errors, averageUlp,stdDevUlp,minUlp,maxUlp);
	}

	public float getErrors() {
		return errors;
	}

	public float getAverageUlp() {
		return averageUlp;
	}

	public float getMinUlp() {
		return minUlp;
	}

	public float getMaxUlp() {
		return maxUlp;
	}

	public float getStdDevUlp() {
		return stdDevUlp;
	}
}
