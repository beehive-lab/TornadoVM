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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * The {@code VectorStoreNode} represents a vector-write to contiguous set of
 * array elements.
 */
@NodeInfo(nameTemplate = "VectorStore")
public final class VectorStoreNode extends AccessIndexedNode {

    public static final NodeClass<VectorStoreNode> TYPE = NodeClass.create(VectorStoreNode.class);

    @Input
    ValueNode value;

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public VectorStoreNode(OCLKind vectorKind, ValueNode array, ValueNode index, ValueNode value) {
        super(TYPE, OCLStampFactory.getStampFor(vectorKind), array, index, JavaKind.Illegal);
        this.value = value;
    }

    @Override
    public JavaKind elementKind() {
        return ((OCLStamp) stamp()).getOCLKind().getElementKind().asJavaKind();
    }

}
