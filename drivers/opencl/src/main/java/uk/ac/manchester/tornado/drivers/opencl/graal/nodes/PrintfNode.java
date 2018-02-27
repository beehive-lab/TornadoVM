/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLPrintf;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.ExprStmt;

@NodeInfo(shortName = "printf")
public class PrintfNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PrintfNode> TYPE = NodeClass
            .create(PrintfNode.class);

    @Input
    private NodeInputList<ValueNode> inputs;

    public PrintfNode(ValueNode... values) {
        super(TYPE, StampFactory.forVoid());
        this.inputs = new NodeInputList<>(this, values.length);
        for (int i = 0; i < values.length; i++) {
            inputs.set(i, values[i]);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] args = new Value[inputs.size()];
        for (int i = 0; i < args.length; i++) {

            ValueNode param = inputs.get(i);
            if (param.isConstant()) {
                args[i] = gen.operand(param);
            } else {
                args[i] = gen.getLIRGeneratorTool().load(gen.operand(param));
            }
        }
        gen.getLIRGeneratorTool().append(new ExprStmt(new OCLPrintf(args)));
    }

}
