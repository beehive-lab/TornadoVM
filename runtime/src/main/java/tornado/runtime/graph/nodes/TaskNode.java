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
package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.List;
import tornado.runtime.graph.GraphAssembler;
import tornado.runtime.graph.GraphCompilationResult;

import static tornado.runtime.graph.GraphAssembler.encodeBlockingMode;

public class TaskNode extends FixedDeviceOpNode {

    private AbstractNode[] arguments;
    private int taskIndex;

    public TaskNode(DeviceNode context, int index, AbstractNode[] arguments) {
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
        for (AbstractNode input : arguments) {
            inputs.add(input);
        }
        return inputs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("]: ");
        sb.append("task=").append(taskIndex);
        sb.append(", args=[ ");
        for (AbstractNode arg : arguments) {
            sb.append("").append(arg.getId()).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

    public int getNumArgs() {
        return arguments.length;
    }

    @Override
    public void emit(GraphCompilationResult compRes) {
        final GraphAssembler asm = compRes.getAssembler();

        final byte mode = encodeBlockingMode(getBlocking(), (byte) 0);

        asm.launch(mode, taskIndex, getDevice().getIndex(), getTaskIndex(), getNumArgs(), compRes.nextEventQueue());

        for (AbstractNode argNode : arguments) {
            if (argNode instanceof ReadHostNode) {
                asm.pushArg(((ReadHostNode) argNode).getValue().getIndex());
            } else if (argNode instanceof ParameterNode) {
                asm.pushArg(((ParameterNode) argNode).getIndex());
            } else if (argNode instanceof WriteHostNode) {
                asm.pushArg(((WriteHostNode) argNode).getValue().getIndex());
            } else if (argNode instanceof AllocateNode) {
                asm.pushArg(((AllocateNode) argNode).getValue().getIndex());
            }
        }
    }

}
