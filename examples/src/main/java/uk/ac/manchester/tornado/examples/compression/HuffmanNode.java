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

package uk.ac.manchester.tornado.examples.compression;

import java.io.Serializable;

public class HuffmanNode implements Serializable {

    public static final int DEFAULT_FREQUENCY = -1;

    private static final long serialVersionUID = 1L;
    int frequency;
    int realData;

    public HuffmanNode() {
        this.frequency = DEFAULT_FREQUENCY;
    }

    HuffmanNode left;
    HuffmanNode right;

    @Override
    public String toString() {
        return "REAL DATA --> " + realData;
    }
}
