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

/**
 * One control-flow edge of a terminator: the target block and the values passed to its arguments.
 * Edits go through the owning {@link Inst} so that use lists stay consistent.
 */
public final class Successor {

    private Block target;
    private final List<Value> args;

    Successor(Block target, List<Value> args) {
        this.target = target;
        this.args = new ArrayList<>(args);
    }

    public Block target() {
        return target;
    }

    public List<Value> args() {
        return Collections.unmodifiableList(args);
    }

    void setTarget(Block target) {
        this.target = target;
    }

    List<Value> mutableArgs() {
        return args;
    }
}
