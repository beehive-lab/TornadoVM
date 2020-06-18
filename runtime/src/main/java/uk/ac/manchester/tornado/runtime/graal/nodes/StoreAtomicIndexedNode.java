/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(nameTemplate = "AtomicIndexedStore")
public final class StoreAtomicIndexedNode extends AccessIndexedNode implements StateSplit, Lowerable, Virtualizable {

    public static final NodeClass<StoreAtomicIndexedNode> TYPE = NodeClass.create(StoreAtomicIndexedNode.class);

    //@formatter:off
    @Input ValueNode value;
    @Input ValueNode accumulator;
    @Input ValueNode inputArray;
    @Input StoreAtomicIndexedNodeExtension storeAtomicExtraNode;
    //@formatter:on

    @Override
    public FrameState stateAfter() {
        return storeAtomicExtraNode.getStateAfter();
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(storeAtomicExtraNode.getStateAfter(), x);
        storeAtomicExtraNode.setStateAfter(x);
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public StoreAtomicIndexedNode(ValueNode outputArray, ValueNode index, JavaKind elementKind, GuardingNode boundsCheck, ValueNode value, ValueNode accumulator, ValueNode inputArray,
            StoreAtomicIndexedNodeExtension extension) {
        super(TYPE, StampFactory.forVoid(), outputArray, index, boundsCheck, elementKind);
        this.value = value;
        this.accumulator = accumulator;
        this.inputArray = inputArray;
        this.storeAtomicExtraNode = extension;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw new RuntimeException("StoreAtomic Virtual Node not supported yet");
    }

    public FrameState getState() {
        return storeAtomicExtraNode.getStateAfter();
    }

    public ValueNode getAccumulator() {
        return accumulator;
    }

    public ValueNode getStartNode() {
        return storeAtomicExtraNode.getStartNode();
    }

    public ValueNode getInputArray() {
        return inputArray;
    }

    public void setOptionalOperation(ValueNode node) {
        storeAtomicExtraNode.setExtraOperation(node);
    }

    public ValueNode getExtraOperation() {
        return storeAtomicExtraNode.getExtraOperation();
    }

    public StoreAtomicIndexedNodeExtension getStoreAtomicExtraNode() {
        return storeAtomicExtraNode;
    }
}
