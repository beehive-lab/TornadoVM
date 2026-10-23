/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.enums;

public enum CUDALocalMemType {

    CL_LOCAL(1), CL_GLOBAL(2);

    private final int value;

    CUDALocalMemType(final int v) {
        value = v;
    }

    public long getValue() {
        return value;
    }

    public static final CUDALocalMemType toLocalMemType(final int v) {
        CUDALocalMemType result = null;
        switch (v) {
            case 1:
                result = CUDALocalMemType.CL_LOCAL;
                break;
            case 2:
                result = CUDALocalMemType.CL_GLOBAL;
                break;
        }
        return result;
    }
}
