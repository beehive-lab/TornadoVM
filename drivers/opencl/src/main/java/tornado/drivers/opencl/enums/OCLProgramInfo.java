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
package tornado.drivers.opencl.enums;

public enum OCLProgramInfo {

	CL_PROGRAM_REFERENCE_COUNT(0x1160),
	CL_PROGRAM_CONTEXT(0x1161),
	CL_PROGRAM_NUM_DEVICES(0x1162),
	CL_PROGRAM_DEVICES(0x1163),
	CL_PROGRAM_SOURCE(0x1164),
	CL_PROGRAM_BINARY_SIZES(0x1165),
	CL_PROGRAM_BINARIES(0x1166),
	CL_PROGRAM_NUM_KERNELS(0x1167),
	CL_PROGRAM_KERNEL_NAMES(0x1168);

	private final int	value;

	OCLProgramInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
