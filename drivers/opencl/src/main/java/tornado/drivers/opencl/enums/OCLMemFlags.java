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

public class OCLMemFlags {
	public static final long	CL_MEM_READ_WRITE		= (1 << 0);
	public static final long	CL_MEM_WRITE_ONLY		= (1 << 1);
	public static final long	CL_MEM_READ_ONLY		= (1 << 2);
	public static final long	CL_MEM_USE_HOST_PTR		= (1 << 3);
	public static final long	CL_MEM_ALLOC_HOST_PTR	= (1 << 4);
	public static final long	CL_MEM_COPY_HOST_PTR	= (1 << 5);
	// reserved (1 << 6)
	public static final long	CL_MEM_HOST_WRITE_ONLY	= (1 << 7);
	public static final long	CL_MEM_HOST_READ_ONLY	= (1 << 8);
	public static final long	CL_MEM_HOST_NO_ACCESS	= (1 << 9);
}
