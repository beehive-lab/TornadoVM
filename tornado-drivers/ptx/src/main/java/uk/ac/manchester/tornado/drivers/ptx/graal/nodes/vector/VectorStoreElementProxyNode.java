/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(nameTemplate = "Store .s{p#lane}")
public final class VectorStoreElementProxyNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<VectorStoreElementProxyNode> TYPE = NodeClass.create(VectorStoreElementProxyNode.class);

    @Input
    ValueNode value;

    @OptionalInput(InputType.Association)
    ValueNode origin;
    @OptionalInput(InputType.Association)
    ValueNode laneOrigin;

    public ValueNode value() {
        return value;
    }

    private VectorStoreElementProxyNode(NodeClass<? extends VectorStoreElementProxyNode> c, PTXKind kind, ValueNode origin, ValueNode lane) {
        super(c, PTXStampFactory.getStampFor(kind));
        this.origin = origin;
        this.laneOrigin = lane;

    }

    public boolean tryResolve() {
        if (canResolve()) {
            /*
             * If we can resolve this node properly, this operation should be applied to the
             * vector node and this node should be discarded.
             */
            final VectorValueNode vector = (VectorValueNode) origin;
            vector.setElement(laneOrigin.asJavaConstant().asInt(), value);
            clearInputs();
            return true;
        } else {
            return false;
        }
    }

    public VectorStoreElementProxyNode(PTXKind kind, ValueNode origin, ValueNode lane, ValueNode value) {
        this(TYPE, kind, origin, lane);
        this.value = value;
    }

    @Override
    public boolean inferStamp() {
        return true;
    }

    public boolean canResolve() {
        return ((origin != null && laneOrigin != null) && origin instanceof VectorValueNode && laneOrigin instanceof ConstantNode
                && ((VectorValueNode) origin).getPTXKind().getVectorLength() > laneOrigin.asJavaConstant().asInt());
    }

    @Override
    public Node canonical(CanonicalizerTool ct) {
        if (tryResolve()) {
            return null;
        } else {
            return this;
        }
    }

}
