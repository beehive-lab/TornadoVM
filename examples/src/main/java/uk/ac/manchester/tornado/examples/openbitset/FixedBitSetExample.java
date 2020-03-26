/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.examples.openbitset;

import java.util.Random;

import org.apache.lucene.util.FixedBitSet;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class FixedBitSetExample {

    public static FixedBitSet genBitSet(int numWords) {
        long[] bits = new long[numWords];
        Random rand = new Random();
        for (int i = 0; i < numWords; i++) {
            bits[i] = rand.nextLong();
        }

        return new FixedBitSet(bits, numWords);
    }

    public static final void main(String[] args) {

        final int numElements = 64;
        FixedBitSet a = genBitSet(numElements);
        FixedBitSet b = genBitSet(numElements);

        TaskSchedule s0 = new TaskSchedule("s0").task("intersect", FixedBitSet::intersectionCount, a, b);

        s0.execute();

        long value = s0.getReturnValue("intersect");
        System.out.printf("value = %d (%d)\n", value, FixedBitSet.intersectionCount(a, b));

    }

}
