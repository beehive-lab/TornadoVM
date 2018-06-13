/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.util.List;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.enums.Access;
import uk.ac.manchester.tornado.common.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.api.CompilableTask;
import uk.ac.manchester.tornado.runtime.api.LocalObjectState;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.AllocateNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ConstantNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyOutNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ObjectNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.StreamInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;

public class TornadoGraphBuilder {

    public static TornadoGraph buildGraph(ExecutionContext graphContext, ByteBuffer buffer) {
        TornadoGraph graph = new TornadoGraph();
        Access[] accesses = null;
        SchedulableTask task;
        AbstractNode[] args = null;
        ContextNode context = null;
        TaskNode taskNode = null;
        int argIndex = 0;
        int taskIndex = 0;

        final List<Object> constants = graphContext.getConstants();
        final List<Object> objects = graphContext.getObjects();

        final ConstantNode[] constantNodes = new ConstantNode[constants.size()];
        for (int i = 0; i < constants.size(); i++) {
            constantNodes[i] = new ConstantNode(i);
            graph.add(constantNodes[i]);
        }

        final AbstractNode[] objectNodes = new AbstractNode[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            objectNodes[i] = new ObjectNode(i);
            graph.add(objectNodes[i]);
        }

        final List<LocalObjectState> states = graphContext.getObjectStates();

        boolean shouldExit = false;
        while (!shouldExit && buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == TaskSchedule.ARG_LIST) {
                final int size = buffer.getInt();
                args = new AbstractNode[size];
                argIndex = 0;
                taskNode = new TaskNode(context, taskIndex, args);
            } else if (op == TaskSchedule.LOAD_REF) {
                final int variableIndex = buffer.getInt();

                final AbstractNode arg = objectNodes[variableIndex];

                if (!(arg instanceof ContextOpNode)) {

                    if (accesses[argIndex] == Access.WRITE) {
                        final AllocateNode allocateNode = new AllocateNode(context);
                        allocateNode.setValue((ObjectNode) arg);
                        graph.add(allocateNode);
                        context.addUse(allocateNode);
                        args[argIndex] = allocateNode;
                    } else {
                        final ObjectNode objectNode = (ObjectNode) arg;
                        final LocalObjectState state = states.get(objectNode.getIndex());
                        if (state.isStreamIn()) {
                            final StreamInNode streamInNode = new StreamInNode(context);
                            streamInNode.setValue(objectNode);
                            graph.add(streamInNode);
                            context.addUse(streamInNode);
                            args[argIndex] = streamInNode;
                        } else {
                            final CopyInNode copyInNode = new CopyInNode(context);
                            copyInNode.setValue((ObjectNode) arg);
                            graph.add(copyInNode);
                            context.addUse(copyInNode);
                            args[argIndex] = copyInNode;
                        }
                    }

                } else {
                    args[argIndex] = arg;
                }

                final AbstractNode nextAccessNode;
                if (accesses[argIndex] == Access.WRITE || accesses[argIndex] == Access.READ_WRITE) {
                    final DependentReadNode depRead = new DependentReadNode(context);

                    final ObjectNode value;
                    if (objectNodes[variableIndex] instanceof ObjectNode) {
                        value = (ObjectNode) objectNodes[variableIndex];
                    } else if (objectNodes[variableIndex] instanceof DependentReadNode) {
                        value = ((DependentReadNode) objectNodes[variableIndex]).getValue();
                    } else if (objectNodes[variableIndex] instanceof CopyInNode) {
                        value = ((CopyInNode) objectNodes[variableIndex]).getValue();
                    } else if (objectNodes[variableIndex] instanceof AllocateNode) {
                        value = ((AllocateNode) objectNodes[variableIndex]).getValue();
                    } else {
                        value = null;
                    }
                    depRead.setValue(value);
                    depRead.setDependent(taskNode);
                    graph.add(depRead);
                    nextAccessNode = depRead;
                } else {
                    nextAccessNode = args[argIndex];
                }

                objectNodes[variableIndex] = nextAccessNode;
                argIndex++;
            } else if (op == TaskSchedule.LOAD_PRIM) {
                final int variableIndex = buffer.getInt();
                args[argIndex] = constantNodes[variableIndex];
                argIndex++;
            } else if (op == TaskSchedule.LAUNCH) {
                context.addUse(taskNode);
                graph.add(taskNode);
            } else if (op == TaskSchedule.CONTEXT) {
                final int globalTaskId = buffer.getInt();
                taskIndex = buffer.getInt();
                task = graphContext.getTask(taskIndex);

                context = graph.addUnique(new ContextNode(graphContext.getDeviceIndexForTask(globalTaskId)));

                if (task instanceof CompilableTask) {
                    final ResolvedJavaMethod resolvedMethod = TornadoRuntime.getTornadoRuntime().resolveMethod(((CompilableTask) task).getMethod());
                    Sketch sketch = TornadoSketcher.lookup(resolvedMethod);
                    accesses = sketch.getMeta().getArgumentsAccess();
                } else {
                    accesses = task.getArgumentsAccess();
                }
            } else {
                shouldExit = true;
            }
        }

        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).isStreamOut()) {
                if (objectNodes[i] instanceof DependentReadNode) {
                    final DependentReadNode readNode = (DependentReadNode) objectNodes[i];
                    context = readNode.getContext();
                    final CopyOutNode copyOutNode = new CopyOutNode(context);
                    copyOutNode.setValue(readNode);
                    graph.add(copyOutNode);
                    context.addUse(copyOutNode);
                }
            } else if (states.get(i).isStreamIn() && objectNodes[i] instanceof ObjectNode) {
                TornadoInternalError.guarantee(graphContext.getDevices().size() == 1, "unsupported StreamIn operation in multiple device mode");
                final StreamInNode streamInNode = new StreamInNode(context);
                streamInNode.setValue((ObjectNode) objectNodes[i]);
                graph.add(streamInNode);
                context.addUse(streamInNode);
            }
        }
        return graph;
    }
}
