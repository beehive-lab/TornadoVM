/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.util.List;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.common.SchedulableTask;
import tornado.common.enums.Access;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.graph.nodes.*;
import tornado.runtime.sketcher.Sketch;
import tornado.runtime.sketcher.TornadoSketcher;

import static tornado.common.enums.Access.*;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static tornado.runtime.api.TaskGraph.*;

public class GraphBuilder {

    public static Graph buildGraph(ExecutionContext graphContext, ByteBuffer buffer) {
        Graph graph = new Graph();
        Access[] accesses = null;
        SchedulableTask task;
        AbstractNode[] args = null;
        DeviceNode device = null;
        TaskNode taskNode = null;
        int argIndex = 0;
        int taskIndex = 0;

//        final List<Object> constants = graphContext.getConstants();
//        final List<Object> objects = graphContext.getObjects();
        // insert all parameters nodes into the graph
        final List<Object> parameters = graphContext.getParameters();
        final ParameterNode[] parameterNodes = new ParameterNode[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            parameterNodes[i] = new ParameterNode(i);
            graph.add(parameterNodes[i]);
        }

        FixedNode currentNode = graph.getBeginNode();

        boolean shouldExit = false;
        while (!shouldExit && buffer.hasRemaining()) {
            final byte op = buffer.get();
            if (op == ARG_LIST) {
                final int size = buffer.getInt();
                args = new AbstractNode[size];
                argIndex = 0;
                taskNode = new TaskNode(device, taskIndex, args);
            } else if (op == LOAD_REF) {
                final int variableIndex = buffer.getInt();

                final ParameterNode arg = parameterNodes[variableIndex];

                final ReadHostNode copyInNode = new ReadHostNode(device);
                copyInNode.setValue(arg);
                graph.add(copyInNode);
                device.addUse(copyInNode);
                args[argIndex] = copyInNode;
                if (accesses[argIndex] == WRITE || accesses[argIndex] == READ_WRITE || accesses[argIndex] == UNKNOWN) {
                    copyInNode.setExclusive();
                }
//                System.out.printf("task graph: arg[%d] = %s\n", argIndex,
//                        args[argIndex]);
                argIndex++;
            } else if (op == LOAD_PRIM) {
                final int variableIndex = buffer.getInt();
                args[argIndex] = parameterNodes[variableIndex];
                argIndex++;
            } else if (op == LAUNCH) {
                device.addUse(taskNode);
                graph.add(taskNode);
                taskNode.insertAfter(currentNode);
                currentNode = taskNode;

                // generate writes
                for (int i = 0; i < argIndex; i++) {
                    if (accesses[i] == WRITE || accesses[i] == READ_WRITE || accesses[i] == UNKNOWN) {
                        final WriteHostNode writeHostNode = new WriteHostNode(taskNode);
                        writeHostNode.setValue(((ReadHostNode) args[i]).getValue());
                        writeHostNode.insertAfter(currentNode);
                        graph.add(writeHostNode);
                        currentNode = writeHostNode;
                    }
                }

            } else if (op == CONTEXT) {
                final int globalTaskId = buffer.getInt();
//                TornadoDevice device = graphContext.getDeviceForTask(globalTaskId);
                taskIndex = buffer.getInt();
                task = graphContext.getTask(taskIndex);
                device = graph.addUnique(new DeviceNode(graphContext.getDeviceIndexForTask(globalTaskId)));
                if (task instanceof CompilableTask) {
                    final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                            .resolveMethod(((CompilableTask) task).getMethod());
                    Sketch sketch = TornadoSketcher.lookup(resolvedMethod);
                    accesses = sketch.getMeta().getArgumentsAccess();
                } else {
                    accesses = task.getArgumentsAccess();
                }
//                System.out.printf("task graph: new frame on %s for %s\n",
//                        device, task.getName());
            } else {
                shouldExit = true;
            }
        }

        return graph;
    }
}
