/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.lang;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class DotProductBasic {

    public static float[] mult3(int n, float[] a, float[] b) {
        final float[] c = new float[n];
        for (int i = 0; i < n; i++) {
            c[i] = a[i] * b[i];
        }
        return c;
    }

    public static float dot3(int n, float[] a, float[] b) {
        float[] c = mult3(n, a, b);
        float sum = 0;
        for (int i = 0; i < n; i++) {
            sum += c[i];
        }
        return sum;
    }

    public static final void main(String[] args) {
        float[] a = new float[] { 1, 1, 1 };
        float[] b = new float[] { 2, 2, 2 };

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", DotProductBasic::dot3, 3, a, b);

        s0.warmup();
        s0.schedule();

        System.out.printf("result = 0x%x\n", s0.getReturnValue("t0"));

    }

}
