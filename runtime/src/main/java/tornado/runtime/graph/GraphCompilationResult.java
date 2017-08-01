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

import tornado.common.TornadoDevice.CacheMode;
import tornado.runtime.graph.nodes.*;

import static tornado.runtime.graph.GraphAssembler.*;

public class GraphCompilationResult {

    private byte[] code;
    private GraphAssembler asm;
    private int gtid;
    private int nextEventQueue;

    public GraphCompilationResult() {
        code = new byte[1024];
        asm = new GraphAssembler(code);
        gtid = 0;
        nextEventQueue = 0;
    }

    public int nextEventQueue() {
        return nextEventQueue++;
    }

    public GraphAssembler getAssembler() {
        return asm;
    }

    public void setup(int numContexts, int numStacks, int numDeps) {
        asm.setup(numContexts, numStacks, numDeps);
        for (int i = 0; i < numContexts; i++) {
            asm.context(i);
        }
//        asm.begin();
    }

    public void barrier(int dep) {
        asm.barrier(dep);
    }

    public void begin() {
        asm.begin();
    }

    public void end() {
        asm.end();
    }

    public void emitAsyncNode(Graph graph, ExecutionContext context,
            AbstractNode node, int ctx, int depIn) {

//		System.out.printf("emit: %s\n",node);
        if (node instanceof ReadHostNode) {
            final ReadHostNode readHostNode = (ReadHostNode) node;

            byte mode = encodeBlockingMode(readHostNode.getBlocking(), (byte) 0);
            mode = encodeSharingMode(readHostNode.getSharingMode(), mode);
            mode = encodeCacheMode(CacheMode.CACHABLE, mode);

            asm.readHost(mode, readHostNode.getValue().getIndex(), ctx, depIn);
        } else if (node instanceof AllocateNode) {
            asm.allocate(((AllocateNode) node).getValue().getIndex(), ctx);
        } else if (node instanceof WriteHostNode) {
            final WriteHostNode writeHostNode = (WriteHostNode) node;

            byte mode = encodeBlockingMode(writeHostNode.getBlocking(), (byte) 0);
            mode = encodeCacheMode(CacheMode.CACHABLE, mode);

            asm.writeHost(mode, writeHostNode.getValue().getIndex(), ctx, depIn);
        } else if (node instanceof TaskNode) {
            final TaskNode taskNode = (TaskNode) node;

            final byte mode = encodeBlockingMode(taskNode.getBlocking(), (byte) 0);

            asm.launch(mode, gtid, taskNode.getDevice().getIndex(), taskNode.getTaskIndex(), taskNode.getNumArgs(), depIn);
            gtid++;

            emitArgList(graph, context, taskNode);
        }
    }

    private void emitArgList(Graph graph, ExecutionContext context,
            TaskNode taskNode) {

        final int numArgs = taskNode.getNumArgs();
        for (int i = 0; i < numArgs; i++) {
            final AbstractNode argNode = taskNode.getArg(i);
            if (argNode instanceof ConstantNode) {
                asm.constantArg(((ConstantNode) argNode).getIndex());
            } else if (argNode instanceof ReadHostNode) {
                asm.referenceArg(((ReadHostNode) argNode).getValue().getIndex());
            } else if (argNode instanceof ParameterNode) {
                asm.referenceArg(((ParameterNode) argNode).getIndex());
            } else if (argNode instanceof WriteHostNode) {
                asm.referenceArg(((WriteHostNode) argNode).getValue().getIndex());
            } else if (argNode instanceof AllocateNode) {
                asm.referenceArg(((AllocateNode) argNode).getValue().getIndex());
            }
        }

    }

    public void emitAddDep(int dep) {
        asm.addDependency(dep);
    }

    public void dump() {
        asm.dump();
    }

    public byte[] getCode() {
        return code;
    }

    public int getCodeSize() {
        return asm.position();
    }

    void postfetch(int index) {
        asm.postfetch(index);
    }

}
