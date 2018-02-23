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
package tornado.drivers.opencl.enums;

public enum OCLContextInfo {
	CL_CONTEXT_REFERENCE_COUNT(0x1080),
	CL_CONTEXT_DEVICES(0x1081),
	CL_CONTEXT_PROPERTIES(0x1082),
	CL_CONTEXT_NUM_DEVICES(0x1083);

	private final int	value;

	OCLContextInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
