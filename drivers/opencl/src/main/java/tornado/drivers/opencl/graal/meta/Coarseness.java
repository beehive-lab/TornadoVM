/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.graal.meta;

import java.util.Arrays;

import static tornado.common.Tornado.getProperty;

public class Coarseness {

    private final int[] values;

    public Coarseness(int depth) {
        values = new int[depth];

        String str[] = getProperty("tornado.coarseness", "1,1,1").split(",");
        for (int i = 0; i < values.length; i++) {
            values[i] = Integer.parseInt(str[i]);
        }
    }

    public int getCoarseness(int index) {
        return values[index];
    }

    public void setCoarseness(int index, int value) {
        values[index] = value;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
