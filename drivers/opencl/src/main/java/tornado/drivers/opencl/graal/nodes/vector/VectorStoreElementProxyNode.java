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
package tornado.drivers.opencl.graal.nodes.vector;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(nameTemplate = "Store .s{p#lane}")
public final class VectorStoreElementProxyNode extends FixedWithNextNode implements Canonicalizable{

    public static final NodeClass<VectorStoreElementProxyNode> TYPE = NodeClass
            .create(VectorStoreElementProxyNode.class);

    @Input
    ValueNode value;

    @OptionalInput(InputType.Association)
    ValueNode origin;
    @OptionalInput(InputType.Association)
    ValueNode laneOrigin;

    public ValueNode value() {
        return value;
    }

    protected VectorStoreElementProxyNode(
            NodeClass<? extends VectorStoreElementProxyNode> c,
            OCLKind kind,
            ValueNode origin,
            ValueNode lane) {
        super(c, OCLStampFactory.getStampFor(kind));
        this.origin = origin;
        this.laneOrigin = lane;

    }

    public boolean tryResolve() {
        if (canResolve()) {
            /*
             * If we can resolve this node properly, this operation
	     * should be applied to the vector node and this node should be
	     * discarded.
             */
            final VectorValueNode vector = (VectorValueNode) origin;
            vector.setElement(((ConstantNode) laneOrigin).asJavaConstant().asInt(), value);
            clearInputs();
            return true;
        } else {
            return false;
        }

    }

    public VectorStoreElementProxyNode(
            OCLKind kind,
            ValueNode origin,
            ValueNode lane,
            ValueNode value) {
        this(TYPE, kind, origin, lane);
        this.value = value;
    }

    @Override
    public boolean inferStamp() {
        return true;//updateStamp(createStamp(origin, kind.getElementKind()));
    }

    public boolean canResolve() {
        return ((origin != null && laneOrigin != null) && origin instanceof VectorValueNode && laneOrigin instanceof ConstantNode);
    }

    public ValueNode getOrigin() {
        return origin;
    }

    public void setOrigin(ValueNode value) {
        origin = value;
    }

    public int getLane() {
//		System.out.printf("vector store proxy: this=%s, origin=%s\n",this,laneOrigin);
        return ((ConstantNode) laneOrigin).asJavaConstant().asInt();
    }

    @Override
    public Node canonical(CanonicalizerTool ct) {
        if(tryResolve()){
            return null;
        } else {
            return this;
        }
    }

}
