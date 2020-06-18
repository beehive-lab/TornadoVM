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

package uk.ac.manchester.tornado.examples.functional;

import java.util.function.Function;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class Operators {

    public static <T1, T2> void map(Function<T1, T2> task, T1[] input, T2[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = task.apply(input[i]);
        }
    }

}
