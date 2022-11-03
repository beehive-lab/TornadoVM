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

public class TaskNode extends ContextOpNode {

    private final AbstractNode[] arguments;
    private final int taskIndex;

    public TaskNode(ContextNode context, int index, AbstractNode[] arguments) {
        super(context);
        this.taskIndex = index;
        this.arguments = arguments;
    }

    public AbstractNode getArg(int index) {
        return arguments[index];
    }

    public int getTaskIndex() {
        return taskIndex;
    }

    @Override
    public List<AbstractNode> getInputs() {
        final List<AbstractNode> inputs = new ArrayList<>();
        Collections.addAll(inputs, arguments);
        return inputs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("]: ");
        sb.append("task=").append(taskIndex);
        sb.append(", args=[ ");
        for (AbstractNode arg : arguments) {
            sb.append("" + arg.getId() + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    public int getNumArgs() {
        return arguments.length;
    }

}
