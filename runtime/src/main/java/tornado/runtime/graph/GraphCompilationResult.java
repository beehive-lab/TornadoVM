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

import tornado.runtime.graph.nodes.*;

import static tornado.common.Tornado.getProperty;

public class GraphCompilationResult {

    public static final int MAX_TVM_BYTECODE_SIZE = Integer.parseInt(getProperty("tornado.tvm.maxbytecodesize", "1024"));

    private byte[] code;
    private GraphAssembler asm;
    private int gtid;

    public GraphCompilationResult() {
        code = new byte[MAX_TVM_BYTECODE_SIZE];
        asm = new GraphAssembler(code);
        gtid = 0;
    }

    public void begin(int numContexts, int numStacks, int numDeps) {
        asm.setup(numContexts, numStacks, numDeps);
        for (int i = 0; i < numContexts; i++) {
            asm.context(i);
        }
        asm.begin();
    }

    public void barrier(int dep) {
        asm.barrier(dep);
    }

    public void end() {
        asm.end();
    }

    public void emitAsyncNode(Graph graph, ExecutionContext context,
            AbstractNode node, int ctx, int depIn) {

//		System.out.printf("emit: %s\n",node);
        if (node instanceof CopyInNode) {
            asm.copyToContext(((CopyInNode) node).getValue().getIndex(), ctx, depIn);
        } else if (node instanceof AllocateNode) {
            asm.allocate(((AllocateNode) node).getValue().getIndex(), ctx);
        } else if (node instanceof CopyOutNode) {
            asm.streamOutOfContext(((CopyOutNode) node).getValue().getValue().getIndex(), ctx, depIn);
        } else if (node instanceof StreamInNode) {
            asm.streamInToContext(((StreamInNode) node).getValue().getIndex(), ctx, depIn);
        } else if (node instanceof TaskNode) {
            final TaskNode taskNode = (TaskNode) node;

            asm.launch(gtid, taskNode.getContext().getIndex(), taskNode.getTaskIndex(), taskNode.getNumArgs(), depIn);
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
            } else if (argNode instanceof CopyInNode) {
                asm.referenceArg(((CopyInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof StreamInNode) {
                asm.referenceArg(((StreamInNode) argNode).getValue().getIndex());
            } else if (argNode instanceof CopyOutNode) {
                asm.referenceArg(((CopyOutNode) argNode).getValue().getValue().getIndex());
            } else if (argNode instanceof AllocateNode) {
                asm.referenceArg(((AllocateNode) argNode).getValue().getIndex());
            } else if (argNode instanceof DependentReadNode) {
                asm.referenceArg(((DependentReadNode) argNode).getValue().getIndex());
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

}
