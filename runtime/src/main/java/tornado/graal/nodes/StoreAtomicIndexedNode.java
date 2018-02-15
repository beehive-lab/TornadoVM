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
 */
package tornado.graal.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.State;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(nameTemplate = "AtomicIndexedStore")
public final class StoreAtomicIndexedNode extends AccessIndexedNode implements StateSplit, Lowerable, Virtualizable { 

    public static final NodeClass<StoreAtomicIndexedNode> TYPE = NodeClass.create(StoreAtomicIndexedNode.class);
    @Input ValueNode value;
    @OptionalInput(State) FrameState stateAfter;
    @Input ValueNode accumulator;

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public StoreAtomicIndexedNode(ValueNode array, ValueNode index, JavaKind elementKind, ValueNode value, ValueNode accumulator) {
        super(TYPE, StampFactory.forVoid(), array, index, elementKind);
        this.value = value;
        this.accumulator = accumulator;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw new RuntimeException("StoreAtomic Virtual Node not supported yet");
    }

    public FrameState getState() {
        return stateAfter;
    }
    
    public ValueNode getAccumulator() {
    	return accumulator;
    }

}

