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
package uk.ac.manchester.tornado.drivers.opencl.enums;

public enum OCLLocalMemType {

	CL_LOCAL(1),
	CL_GLOBAL(2);

	private final int	value;

	OCLLocalMemType(final int v) {
		value = v;
	}

	public long getValue() {
		return value;
	}

	public static final OCLLocalMemType toLocalMemType(final int v) {
		OCLLocalMemType result = null;
		switch (v) {
			case 1 :
				result = OCLLocalMemType.CL_LOCAL;
				break;
			case 2:
				result = OCLLocalMemType.CL_GLOBAL;
				break;
		}
		return result;
	}
}
