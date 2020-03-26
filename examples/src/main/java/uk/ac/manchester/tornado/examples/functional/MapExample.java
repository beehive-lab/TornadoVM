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

import java.util.stream.IntStream;

public class MapExample {

    public static Integer inc(Integer value) {
        return value + 1;
    }

    public static final void main(String[] args) {
        int numElements = 8;
        Integer[] a = new Integer[numElements];
        Integer[] b = new Integer[numElements];

        IntStream.range(0, numElements).forEach(idx -> a[idx] = idx);

        Operators.map(MapExample::inc, a, b);

    }

}
