/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.Access;

/**
 * Definition of accessor for the Prebuilt-Task API.
 * It defines the accessor of each object for all input parameter to the task.
 */
public class AccessorParameters {

    private final PairAccessor[] accessors;

    public AccessorParameters(int numParameters) {
        accessors = new PairAccessor[numParameters];
    }

    public void set(int index, Object object, Access access) {
        accessors[index] = new PairAccessor(object, access);
    }

    public int numAccessors() {
        return accessors.length;
    }

    public PairAccessor getAccessor(int index) {
        return accessors[index];
    }

    public record PairAccessor(Object object, Access access) {
    }
}
