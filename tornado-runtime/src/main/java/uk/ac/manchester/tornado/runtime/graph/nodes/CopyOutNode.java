/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CopyOutNode extends ContextOpNode {

    private DependentReadNode value;

    public CopyOutNode(ContextNode context) {
        super(context);
    }

    public DependentReadNode getValue() {
        return value;
    }

    public void setValue(DependentReadNode object) {
        value = object;
    }

    @Override
    public String toString() {
        return String.format("[%d]: copy out object %d after task %d", id, value.getValue().getIndex(), value.getDependent().getId());
    }

    @Override
    public boolean hasInputs() {
        return value != null;
    }

    @Override
    public List<AbstractNode> getInputs() {
        if (!hasInputs()) {
            return Collections.emptyList();
        }

        final List<AbstractNode> result = new ArrayList<>();
        result.add(value);
        return result;
    }
}
