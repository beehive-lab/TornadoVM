/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(nameTemplate = "AtomicWrite")
public class WriteAtomicNode extends FixedWithNextNode implements StateSplit, Lowerable {

    public static final NodeClass<WriteAtomicNode> TYPE = NodeClass.create(WriteAtomicNode.class);

    //@formatter:off
    @Input ValueNode value;
    @Input ValueNode accumulator;
    @Input ValueNode inputArray;
    @Input WriteAtomicNodeExtension writeAtomicExtraNode;
    @Input AddressNode address;
    @Input ValueNode outArray;
    JavaKind kind;
    //@formatter:on

    public WriteAtomicNode(JavaKind kind, AddressNode address, ValueNode value, ValueNode accumulator, ValueNode inputArray, ValueNode outArray, WriteAtomicNodeExtension extension) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
        this.accumulator = accumulator;
        this.inputArray = inputArray;
        this.writeAtomicExtraNode = extension;
        this.address = address;
        this.kind = kind;
        this.outArray = outArray;
    }

    public ValueNode getIndex() {
        return null;
    }

    public JavaKind getElementKind() {
        return kind;
    }

    public ValueNode value() {
        return value;
    }

    public ValueNode getAccumulator() {
        return accumulator;
    }

    public ValueNode getStartNode() {
        return writeAtomicExtraNode.getStartNode();
    }

    public ValueNode getInputArray() {
        return inputArray;
    }

    public void setOptionalOperation(ValueNode node) {
        writeAtomicExtraNode.setExtraOperation(node);
    }

    public ValueNode getExtraOperation() {
        return writeAtomicExtraNode.getExtraOperation();
    }

    public ValueNode getOutArray() {
        return outArray;
    }

    @Override
    public FrameState stateAfter() {
        return writeAtomicExtraNode.getStateAfter();
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(writeAtomicExtraNode.getStateAfter(), x);
        writeAtomicExtraNode.setStateAfter(x);
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

}
