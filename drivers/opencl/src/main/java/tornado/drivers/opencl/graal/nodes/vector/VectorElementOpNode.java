/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
package tornado.drivers.opencl.graal.nodes.vector;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLVectorElementSelect;

import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(nameTemplate = "Op .s{p#lane}")
public abstract class VectorElementOpNode extends FloatingNode implements LIRLowerable, Comparable<VectorElementOpNode> {

    public static final NodeClass<VectorElementOpNode> TYPE = NodeClass
            .create(VectorElementOpNode.class);

    @Input(InputType.Extension)
    ValueNode vector;

    @Input
    ValueNode lane;

    protected final OCLKind oclKind;

    protected VectorElementOpNode(NodeClass<? extends VectorElementOpNode> c, OCLKind kind, ValueNode vector, ValueNode lane) {
        super(c, StampFactory.forKind(kind.asJavaKind()));
        this.oclKind = kind;
        this.vector = vector;
        this.lane = lane;

        Stamp vstamp = vector.stamp();
        OCLKind vectorKind = OCLKind.ILLEGAL;
        if (vstamp instanceof ObjectStamp) {
            ObjectStamp ostamp = (ObjectStamp) vector.stamp();
//            System.out.printf("ostamp: type=%s\n", ostamp.type());

            if (ostamp.type() != null) {
                vectorKind = OCLKind.fromResolvedJavaType(ostamp.type());
                guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
                guarantee(vectorKind.getVectorLength() >= laneId(), "Invalid lane %d on type %s", laneId(), oclKind);
            }
        } else if (vstamp instanceof OCLStamp) {
            final OCLStamp vectorStamp = (OCLStamp) vector.stamp();
            vectorKind = vectorStamp.getOCLKind();
            guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
            guarantee(vectorKind.getVectorLength() >= laneId(), "Invalid lane %d on type %s", laneId(), oclKind);
        } else {
            shouldNotReachHere("invalid type on vector operation: %s (stamp=%s (class=%s))", vector, vector.stamp(), vector.stamp().getClass().getName());
        }

    }

    @Override
    public int compareTo(VectorElementOpNode o) {
        return Integer.compare(laneId(), o.laneId());
    }

    @Override
    public boolean inferStamp() {
//        return false;
        return updateStamp(StampFactory.forKind(oclKind.asJavaKind()));
    }

    final public int laneId() {
        guarantee(lane instanceof ConstantNode, "Invalid lane: %s", lane);
        return (lane instanceof ConstantNode) ? lane.asJavaConstant().asInt() : -1;
    }

    public ValueNode getVector() {
        return vector;
    }

    public OCLKind getOCLKind() {
        return oclKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector is null");
//		System.out.printf("vector = %s, origin=%s\n",vector,vector.getOrigin());
        Value targetVector = gen.operand(getVector());
//        if (targetVector == null && vector.getOrigin() instanceof Invoke) {
//            targetVector = gen.operand(vector.getOrigin());
//        }

        guarantee(targetVector != null, "vector is null 2");
        final OCLVectorElementSelect element = new OCLVectorElementSelect(gen.getLIRGeneratorTool().getLIRKind(stamp), targetVector, new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(laneId())));
        gen.setResult(this, element);

    }

}
