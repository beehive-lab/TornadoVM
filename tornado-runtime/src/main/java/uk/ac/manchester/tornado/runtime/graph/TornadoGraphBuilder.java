/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.AllocateMultipleBuffersNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.AllocateNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ConstantNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.CopyOutNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DeallocateNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ObjectNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.StreamInNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.LocalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.TornadoGraphBitcodes;

public class TornadoGraphBuilder {

    private static void createStreamInNode(ContextNode context, TornadoGraph graph, ObjectNode arg, AbstractNode[] args, int argIndex, AllocateMultipleBuffersNode persistNode) {
        final StreamInNode streamInNode = new StreamInNode(context);
        streamInNode.setValue(arg);
        graph.add(streamInNode);
        context.addUse(streamInNode);
        args[argIndex] = streamInNode;
        persistNode.addValue(arg);
    }

    private static void createAllocateNode(ContextNode context, TornadoGraph graph, AbstractNode arg, AbstractNode[] args, int argIndex, AllocateMultipleBuffersNode persistNode) {
        final AllocateNode allocateNode = new AllocateNode(context);
        allocateNode.setValue((ObjectNode) arg);
        graph.add(allocateNode);
        context.addUse(allocateNode);
        args[argIndex] = allocateNode;
        persistNode.addValue((ObjectNode) arg);
    }

    private static void createCopyInNode(ContextNode context, TornadoGraph graph, AbstractNode arg, AbstractNode[] args, int argIndex, AllocateMultipleBuffersNode persistNode) {
        final CopyInNode copyInNode = new CopyInNode(context);
        copyInNode.setValue((ObjectNode) arg);
        graph.add(copyInNode);
        context.addUse(copyInNode);
        args[argIndex] = copyInNode;
        persistNode.addValue((ObjectNode) arg);
    }

    private static boolean shouldPerformSharedObjectCopy(AbstractNode arg, ContextNode contextNode) {
        return ((ContextOpNode) arg).getContext().getUses().size() != 1 && contextNode.getDeviceIndex() != ((ContextOpNode) arg).getContext().getDeviceIndex();
    }

    /**
     * It constructs a {@link TornadoGraph} from the provided
     * {@link TornadoExecutionContext} and ByteBuffer.
     *
     * @param executionContext
     *     The {@link TornadoExecutionContext} that contains the context of
     *     the graph.
     * @param buffer
     *     The {@link ByteBuffer} containing the bytecode representation of
     *     the graph.
     * @return The constructed {@link TornadoGraph}.
     */
    public static TornadoGraph buildGraph(TornadoExecutionContext executionContext, ByteBuffer buffer) {
        TornadoGraph graph = new TornadoGraph();
        Access[] accesses = null;
        SchedulableTask task;
        AbstractNode[] args = null;
        ContextNode context = null;
        AllocateMultipleBuffersNode persist = null;
        TaskNode taskNode = null;
        int argIndex = 0;
        int taskIndex = 0;

        final List<Object> constants = executionContext.getConstants();
        final List<Object> objects = executionContext.getObjects();

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

        final List<LocalObjectState> states = executionContext.getObjectStates();

        boolean shouldExit = false;
        while (!shouldExit && buffer.hasRemaining()) {
            final byte op = buffer.get();

            if (op == TornadoGraphBitcodes.ARG_LIST.index()) {
                final int size = buffer.getInt();
                args = new AbstractNode[size];
                argIndex = 0;
                taskNode = new TaskNode(context, taskIndex, args);
            } else if (op == TornadoGraphBitcodes.LOAD_REF.index()) {
                final int variableIndex = buffer.getInt();

                final AbstractNode arg = objectNodes[variableIndex];
                if (!(arg instanceof ContextOpNode)) {
                    if (Objects.requireNonNull(accesses)[argIndex] == Access.WRITE_ONLY) {
                        createAllocateNode(context, graph, arg, args, argIndex, persist);
                    } else {
                        final ObjectNode objectNode = (ObjectNode) arg;
                        final LocalObjectState state = states.get(objectNode.getIndex());
                        if (state.isStreamIn()) {
                            createStreamInNode(context, graph, objectNode, args, argIndex, persist);
                        } else {
                            if (!state.isUnderDemand()) {
                                createCopyInNode(context, graph, arg, args, argIndex, persist);
                            } else {
                                // if it is under demand, we only need to allocate the buffer
                                createAllocateNode(context, graph, arg, args, argIndex, persist);
                            }
                        }
                    }
                } else {
                    if (shouldPerformSharedObjectCopy(arg, context)) {
                        createCopyInNode(context, graph, arg.getInputs().get(0), args, argIndex, persist);
                    }
                    args[argIndex] = arg;
                }

                final AbstractNode nextAccessNode;
                assert accesses != null;
                if (accesses[argIndex] == Access.WRITE_ONLY || accesses[argIndex] == Access.READ_WRITE) {
                    final DependentReadNode depRead = new DependentReadNode(context);
                    final ObjectNode value;
                    if (objectNodes[variableIndex] instanceof ObjectNode) {
                        value = (ObjectNode) objectNodes[variableIndex];
                    } else if (objectNodes[variableIndex] instanceof DependentReadNode) {
                        value = ((DependentReadNode) objectNodes[variableIndex]).getValue();
                        if (states.get(variableIndex).isForcedStreamIn()) {
                            createStreamInNode(context, graph, value, args, argIndex, persist);
                        }
                    } else if (objectNodes[variableIndex] instanceof CopyInNode) {
                        value = ((CopyInNode) objectNodes[variableIndex]).getValue();
                    } else if (objectNodes[variableIndex] instanceof AllocateNode) {
                        value = ((AllocateNode) objectNodes[variableIndex]).getValue();
                    } else if (objectNodes[variableIndex] instanceof StreamInNode) {
                        value = ((StreamInNode) objectNodes[variableIndex]).getValue();
                    } else {
                        throw new TornadoRuntimeException("Invalid graph node in TornadoGraph builder for node: " + objectNodes[variableIndex].getClass().getName());
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

                // end-of load reference condition

            } else if (op == TornadoGraphBitcodes.LOAD_PRIM.index()) {
                final int variableIndex = buffer.getInt();
                args[argIndex] = constantNodes[variableIndex];
                argIndex++;
            } else if (op == TornadoGraphBitcodes.LAUNCH.index()) {
                context.addUse(taskNode);
                graph.add(taskNode);
            } else if (op == TornadoGraphBitcodes.CONTEXT.index()) {
                final int globalTaskId = buffer.getInt();
                taskIndex = buffer.getInt();
                task = executionContext.getTask(taskIndex);

                /*
                 * Note, {@code executionContext.getDevices().indexOf} retrieves the device
                 * index in the {@code Device[]} array, which is different from the device index
                 * that appears in the output of the Tornado devices command. So, internally, we
                 * refer to the device index in the {@code Device[]} array and not in the output
                 * of the Tornado devices command.
                 *
                 * For example, in case of three backends with three devices of 0:0, 1:0, 2:0,
                 * the {@code Devices[]} array will have indexes from 0 to 2, but the order of
                 * the devices is not guaranteed. It relies on the order that tasks are added to
                 * the execution context. So, the first device for {@code t0}, then the device
                 * for {@code t2}, etc., may occupy indexes 0, 1, etc. respectively in the
                 * array.
                 *
                 */
                TornadoXPUDevice deviceForTask = executionContext.getDeviceForTask(taskIndex);
                context = graph.addUnique(new ContextNode(executionContext.getDevices().indexOf(deviceForTask), deviceForTask));

                persist = graph.addUnique(new AllocateMultipleBuffersNode(context));
                context.addUse(persist);

                if (task instanceof CompilableTask) {
                    final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(((CompilableTask) task).getMethod());
                    Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());
                    accesses = sketch.getArgumentsAccess();
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
                final StreamInNode streamInNode = new StreamInNode(context);
                streamInNode.setValue((ObjectNode) objectNodes[i]);
                graph.add(streamInNode);
                assert context != null;
                context.addUse(streamInNode);
                assert persist != null;
                persist.addValue((ObjectNode) objectNodes[i]);
            }
        }

        // Add deallocate nodes to the graph for each copy-in/allocate/stream-in
        final BitSet asyncNodes = graph.filter(ContextOpNode.class::isInstance);
        int dependencyIndex = asyncNodes.previousSetBit(asyncNodes.length() - 1);
        ContextOpNode dependencyNode = (ContextOpNode) graph.getNode(dependencyIndex);
        for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
            ContextOpNode node = (ContextOpNode) graph.getNode(i);
            if (node instanceof CopyInNode || node instanceof AllocateNode || node instanceof StreamInNode) {
                ObjectNode objectNode;
                if (node instanceof CopyInNode) {
                    objectNode = ((CopyInNode) node).getValue();
                } else if (node instanceof AllocateNode) {
                    objectNode = ((AllocateNode) node).getValue();
                } else {
                    objectNode = ((StreamInNode) node).getValue();
                }
                ContextNode contextNode = node.getContext();
                DeallocateNode deallocateNode = new DeallocateNode(contextNode);
                deallocateNode.setValue(objectNode);
                deallocateNode.setDependent(dependencyNode);
                graph.add(deallocateNode);
                contextNode.addUse(deallocateNode);
            }
        }

        return graph;
    }
}
