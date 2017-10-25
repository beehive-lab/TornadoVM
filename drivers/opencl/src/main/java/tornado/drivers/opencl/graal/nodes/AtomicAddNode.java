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
package tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(shortName = "Atomic Add")
public class AtomicAddNode extends AccessIndexedNode implements Lowerable {

    public static final NodeClass<AtomicAddNode> TYPE = NodeClass
            .create(AtomicAddNode.class);

    @Input
    ValueNode value;

    public AtomicAddNode(
            ValueNode array,
            ValueNode index,
            JavaKind elementKind,
            ValueNode value
    ) {
        super(TYPE, StampFactory.forVoid(), array, index, elementKind);
        this.value = value;
    }

    public ValueNode value() {
        return value;
    }

}
