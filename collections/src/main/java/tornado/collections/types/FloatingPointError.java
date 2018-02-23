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
package tornado.collections.types;

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
