/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.common;

import java.util.HashMap;
import java.util.List;

public interface KernelStackFrame {

    // Marks an argument of type KernelContext being passed explicitly as a parameter.
    class KernelContextArgument {
    }

    class CallArgument {
        private final Object value;
        private final boolean isReferenceType;

        public CallArgument(Object value, boolean isReferenceType) {
            this.value = value;
            this.isReferenceType = isReferenceType;
        }

        public Object getValue() {
            return value;
        }

        public boolean isReferenceType() {
            return isReferenceType;
        }
    }

    void reset();

    List<CallArgument> getCallArguments();

    void addCallArgument(Object value, boolean isReferenceType);

    void setKernelContext(HashMap<Integer, Integer> map);

    boolean isValid();

    void invalidate();
}
