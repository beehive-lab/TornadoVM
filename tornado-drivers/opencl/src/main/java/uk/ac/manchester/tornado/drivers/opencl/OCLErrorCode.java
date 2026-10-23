/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

public class OCLErrorCode {

    public static final int CL_SUCCESS = 0;
    public static final int CL_DEVICE_NOT_FOUND = -1;
    public static final int CL_DEVICE_NOT_AVAILABLE = -2;
    public static final int CL_COMPILER_NOT_AVAILABLE = -3;
    // TODO: complete this list
    public static final int CL_INVALID_KERNEL_ARGS = -52;
    public static final int CL_INVALID_WORK_GROUP_SIZE = -54;
}
