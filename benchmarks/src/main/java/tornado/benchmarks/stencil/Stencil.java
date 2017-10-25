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
package tornado.benchmarks.stencil;

import tornado.api.Parallel;

/**
 *
 * @author James Clarkson
 */
public class Stencil {

    public static final void stencil3d(int n, int sz, float[] a0, float[] a1, float fac) {
        for (@Parallel int i = 1; i < n + 1; i++) {
            for (@Parallel int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    a1[i * sz * sz + j * sz + k] = (a0[i * sz * sz + (j - 1) * sz + k] + a0[i * sz * sz + (j + 1) * sz + k]
                            + a0[(i - 1) * sz * sz + j * sz + k] + a0[(i + 1) * sz * sz + j * sz + k]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + k] + a0[(i - 1) * sz * sz + (j + 1) * sz + k]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + k] + a0[(i + 1) * sz * sz + (j + 1) * sz + k]
                            + a0[i * sz * sz + (j - 1) * sz + (k - 1)] + a0[i * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[(i - 1) * sz * sz + j * sz + (k - 1)] + a0[(i + 1) * sz * sz + j * sz + (k - 1)]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + (k - 1)] + a0[(i - 1) * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + (k - 1)] + a0[(i + 1) * sz * sz + (j + 1) * sz + (k - 1)]
                            + a0[i * sz * sz + (j - 1) * sz + (k + 1)] + a0[i * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[(i - 1) * sz * sz + j * sz + (k + 1)] + a0[(i + 1) * sz * sz + j * sz + (k + 1)]
                            + a0[(i - 1) * sz * sz + (j - 1) * sz + (k + 1)] + a0[(i - 1) * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[(i + 1) * sz * sz + (j - 1) * sz + (k + 1)] + a0[(i + 1) * sz * sz + (j + 1) * sz + (k + 1)]
                            + a0[i * sz * sz + j * sz + (k - 1)] + a0[i * sz * sz + j * sz + (k + 1)]) * fac;
                }
            }
        }
    }

    public static final void copy(int sz, float[] a0, float[] a1) {
        for (@Parallel int i = 0; i < a0.length; i++) {
            a1[i] = a0[i];
        }
    }
}
