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
package tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;

@NodeInfo(shortName = "Atomic Write")
public class AtomicWriteNode extends AbstractWriteNode implements LIRLowerable {

    public static final NodeClass<AtomicWriteNode> TYPE = NodeClass
            .create(AtomicWriteNode.class);

    OCLBinaryIntrinsic op;

    public AtomicWriteNode(
            OCLBinaryIntrinsic op,
            AddressNode address, LocationIdentity location, ValueNode value) {
        super(TYPE, address, location, value, BarrierType.NONE);
        this.op = op;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        unimplemented();
//        final LocationNode location = location();
//
//        final Value object = gen.operand(object());
//
//        final MemoryAccess addressOfObject = (MemoryAccess) location.generateAddress(gen, tool,
//                object);
////		addressOfObject.setKind(value().getKind());
//
//        final Value valueToStore = gen.operand(value());
//
//        tool.append(new OCLLIRInstruction.ExprStmt(new OCLBinary.Intrinsic(op, JavaKind.Illegal,
//                addressOfObject, valueToStore)));
//        trace("emitAtomicWrite: %s(%s, %s)", op.toString(),
//                addressOfObject, valueToStore);

    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

}
