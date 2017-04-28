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
package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = ".{p#selection}")
public class VectorElementSelectNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<VectorElementSelectNode> TYPE = NodeClass
            .create(VectorElementSelectNode.class);

    @Input(InputType.Extension)
    ValueNode vector;

    @Input
    ValueNode selection;

    public VectorElementSelectNode(OCLKind kind, ValueNode vector, ValueNode selection) {
        super(TYPE, StampFactory.forKind(kind.asJavaKind()));
        this.vector = vector;
        this.selection = selection;
    }

    @Override
    public boolean inferStamp() {
        return true;
        //return updateStamp(createStamp(vector, kind.getElementKind()));
    }

    public ValueNode getSelection() {
        return selection;
    }

    public ValueNode getVector() {
        return vector;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector operand is null");
        Value targetVector = gen.operand(getVector());
        Value selectValue = gen.operand(getSelection());

        guarantee(targetVector != null, "vector value is null 2");
        guarantee(selectValue != null, "select value is null");
        final OCLBinary.Selector expr = new OCLBinary.Selector(OCLBinaryOp.VECTOR_SELECT, gen.getLIRGeneratorTool().getLIRKind(stamp), targetVector, selectValue);
        gen.setResult(this, expr);

    }

}
