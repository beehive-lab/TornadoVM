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
package tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryTemplate;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class FixedArrayNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass
            .create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected OCLKind elementKind;
    protected OCLMemoryBase memoryRegister;

    public FixedArrayNode(OCLMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementKind = OCLKind.fromResolvedJavaType(elementType);
    }

    public FixedArrayNode(ResolvedJavaType elementType, ConstantNode length) {
        this(OCLArchitecture.hp, elementType, length);
    }

    public OCLMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /*
         * using as_T reinterprets the data as type T - consider: float x =
         * (float) 1; and int value = 1, float x = &(value);
         */
        final Value lengthValue = gen.operand(length);
//        System.out.printf("gen operand: %s (%s)\n", lengthValue, lengthValue.getClass().getName());

        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final OCLBinary.Expr declaration = new OCLBinary.Expr(OCLBinaryTemplate.NEW_ARRAY, lirKind, variable, lengthValue);

        final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);

//        System.out.printf("expr: %s\n", expr);
        gen.getLIRGeneratorTool().append(expr);

        gen.setResult(this, variable);
    }

}
