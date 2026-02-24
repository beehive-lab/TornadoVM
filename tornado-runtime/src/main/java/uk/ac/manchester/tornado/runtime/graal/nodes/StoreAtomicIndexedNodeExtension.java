/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: Florin Blanaru
 *
 */

package uk.ac.manchester.tornado.runtime.graal.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.State;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;

@NodeInfo(nameTemplate = "AtomicIndexedStoreExtension")
public class StoreAtomicIndexedNodeExtension extends FloatingNode {

    public static final NodeClass<StoreAtomicIndexedNodeExtension> TYPE = NodeClass.create(StoreAtomicIndexedNodeExtension.class);

    @OptionalInput(State)
    FrameState stateAfter;
    @OptionalInput
    ValueNode extraOperation;
    @OptionalInput
    ValueNode startNode;

    public StoreAtomicIndexedNodeExtension(ValueNode startNode) {
        super(TYPE, StampFactory.forVoid());
        this.startNode = startNode;
    }

    public FrameState getStateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState stateAfter) {
        this.stateAfter = stateAfter;
    }

    public ValueNode getExtraOperation() {
        return extraOperation;
    }

    public ValueNode getStartNode() {
        return startNode;
    }

    public void setExtraOperation(ValueNode extraOperation) {
        this.extraOperation = extraOperation;
    }
}
