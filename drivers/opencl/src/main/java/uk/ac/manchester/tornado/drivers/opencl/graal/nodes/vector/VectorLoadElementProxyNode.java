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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@Deprecated()
@NodeInfo(nameTemplate = "Load .s{p#lane}")
public final class VectorLoadElementProxyNode extends FixedWithNextNode {

    public static final NodeClass<VectorLoadElementProxyNode> TYPE = NodeClass
            .create(VectorLoadElementProxyNode.class);

    @OptionalInput(InputType.Association)
    ValueNode origin;
    @OptionalInput(InputType.Association)
    ValueNode laneOrigin;

    protected final OCLKind kind;

    protected VectorLoadElementProxyNode(
            NodeClass<? extends VectorLoadElementProxyNode> c,
            OCLKind kind,
            ValueNode origin,
            ValueNode lane) {
        super(c, OCLStampFactory.getStampFor(kind));
        this.kind = kind;
        this.origin = origin;
        this.laneOrigin = lane;
    }

    public VectorLoadElementNode tryResolve() {
        VectorLoadElementNode loadNode = null;
        if (canResolve()) {
            /*
             * If we can resolve this node properly, this operation should be
             * applied to the vector node and this node should be discarded.
             */
            VectorValueNode vector = null;
//				System.out.printf("origin: %s\n",origin);
            if (origin instanceof VectorValueNode) {
                vector = (VectorValueNode) origin;
            } //				else if(origin instanceof ParameterNode){
            //					vector = origin.graph().addOrUnique(new VectorValueNode(kind, origin));
            else {
                shouldNotReachHere();
            }

            loadNode = new VectorLoadElementNode(kind, vector, laneOrigin);
            clearInputs();
        }

        return loadNode;
    }

    public VectorLoadElementProxyNode(VectorValueNode vector, ValueNode lane, ValueNode value) {
        this(TYPE, vector.getOCLKind(), vector, lane);
    }

    public VectorLoadElementProxyNode(
            OCLKind vectorKind,
            ValueNode origin,
            ValueNode lane) {
        this(TYPE, vectorKind, origin, lane);
    }

    @Override
    public boolean inferStamp() {
        return true;
//return updateStamp(createStamp(origin, kind.getElementKind()));
    }

    public OCLKind getOCLKind() {
        return kind;
    }

    public boolean canResolve() {
        return (isOriginResolvable() && laneOrigin != null && laneOrigin instanceof ConstantNode);
    }

    private final boolean isOriginResolvable() {
        return (origin != null && (origin instanceof VectorValueNode));
    }

    public ValueNode getOrigin() {
        return origin;
    }

    public void setOrigin(ValueNode value) {
        updateUsages(origin, value);
        origin = value;
    }

    public int getLane() {
        return ((ConstantNode) laneOrigin).asJavaConstant().asInt();
    }

}
