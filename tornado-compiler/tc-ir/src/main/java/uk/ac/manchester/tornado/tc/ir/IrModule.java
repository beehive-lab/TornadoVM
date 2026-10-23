/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.tc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A compilation unit: the kernel and the device functions it calls. */
public final class IrModule {

    private final List<Function> functions = new ArrayList<>();

    public List<Function> functions() {
        return Collections.unmodifiableList(functions);
    }

    public Function add(Function function) {
        if (find(function.name()).isPresent()) {
            throw new IllegalArgumentException("duplicate function @" + function.name());
        }
        functions.add(function);
        return function;
    }

    public Optional<Function> find(String name) {
        return functions.stream().filter(f -> f.name().equals(name)).findFirst();
    }
}
