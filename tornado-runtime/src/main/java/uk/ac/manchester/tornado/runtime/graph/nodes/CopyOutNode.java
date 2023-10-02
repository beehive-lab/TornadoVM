/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
