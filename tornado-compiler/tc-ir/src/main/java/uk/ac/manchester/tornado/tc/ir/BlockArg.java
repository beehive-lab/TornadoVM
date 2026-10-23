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

/**
 * A block argument: the TC-SSA replacement for a phi node. Predecessors pass a value for it on the
 * edge; the arguments of a function's entry block are its parameters.
 */
public final class BlockArg extends Value {

    private final Block block;

    BlockArg(Block block, Type type, String name) {
        super(type, name);
        this.block = block;
    }

    public Block block() {
        return block;
    }

    public int index() {
        return block.args().indexOf(this);
    }
}
