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
import uk.ac.manchester.tornado.api.collections.types.Float3;

public class DotProduct {

    public static final void main(String[] args) {
        Float3 a = new Float3(1, 1, 1);
        Float3 b = new Float3(2, 2, 2);

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", Float3::dot, a, b);

        s0.warmup();
        s0.schedule();

        System.out.printf("result = 0x%x\n", s0.getReturnValue("t0"));
    }
}
