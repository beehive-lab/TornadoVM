/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.benchmarks.stencil;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/*
 * * @author James Clarkson
 */
public class Stencil {

    public static final void stencil3d(int n, int sz, FloatArray a0, FloatArray a1, float fac) {
        for (@Parallel int i = 1; i < n + 1; i++) {
            for (@Parallel int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    // @formatter:off
                    a1.set(i * sz * sz + j * sz + k, (a0.get(i * sz * sz + (j - 1) * sz + k) + a0.get(i * sz * sz + (j + 1) * sz + k)
                            + a0.get((i - 1) * sz * sz + j * sz + k) + a0.get((i + 1) * sz * sz + j * sz + k)
                            + a0.get((i - 1) * sz * sz + (j - 1) * sz + k) + a0.get((i - 1) * sz * sz + (j + 1) * sz + k)
                            + a0.get((i + 1) * sz * sz + (j - 1) * sz + k) + a0.get((i + 1) * sz * sz + (j + 1) * sz + k)
                            + a0.get(i * sz * sz + (j - 1) * sz + (k - 1)) + a0.get(i * sz * sz + (j + 1) * sz + (k - 1))
                            + a0.get((i - 1) * sz * sz + j * sz + (k - 1)) + a0.get((i + 1) * sz * sz + j * sz + (k - 1))
                            + a0.get((i - 1) * sz * sz + (j - 1) * sz + (k - 1)) + a0.get((i - 1) * sz * sz + (j + 1) * sz + (k - 1))
                            + a0.get((i + 1) * sz * sz + (j - 1) * sz + (k - 1)) + a0.get((i + 1) * sz * sz + (j + 1) * sz + (k - 1))
                            + a0.get(i * sz * sz + (j - 1) * sz + (k + 1)) + a0.get(i * sz * sz + (j + 1) * sz + (k + 1))
                            + a0.get((i - 1) * sz * sz + j * sz + (k + 1)) + a0.get((i + 1) * sz * sz + j * sz + (k + 1))
                            + a0.get((i - 1) * sz * sz + (j - 1) * sz + (k + 1)) + a0.get((i - 1) * sz * sz + (j + 1) * sz + (k + 1))
                            + a0.get((i + 1) * sz * sz + (j - 1) * sz + (k + 1)) + a0.get((i + 1) * sz * sz + (j + 1) * sz + (k + 1))
                            + a0.get(i * sz * sz + j * sz + (k - 1)) + a0.get(i * sz * sz + j * sz + (k + 1))) * fac);
                    // @formatter:on
                }
            }
        }
    }

    public static final void copy(int sz, FloatArray a0, FloatArray a1) {
        for (@Parallel int i = 0; i < a0.getSize(); i++) {
            a1.set(i, a0.get(i));
        }
    }
}
