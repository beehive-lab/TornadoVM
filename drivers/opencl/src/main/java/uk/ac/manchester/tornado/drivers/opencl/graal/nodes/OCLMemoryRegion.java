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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.meta.OCLMemorySpace;
import uk.ac.manchester.tornado.drivers.opencl.graal.meta.OCLStack;

@NodeInfo
public class OCLMemoryRegion extends FloatingNode implements LIRLowerable {

    public static final NodeClass<OCLMemoryRegion> TYPE = NodeClass.create(OCLMemoryRegion.class);

    public static enum Region {
        GLOBAL, LOCAL, PRIVATE, CONSTANT, STACK, HEAP;
    }

    protected Region region;

    public OCLMemoryRegion(Region region) {
        super(TYPE, StampFactory.objectNonNull());
        this.region = region;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value value = null;
        switch (region) {
            case GLOBAL:
                value = OCLMemorySpace.GLOBAL;
                break;
            case LOCAL:
                value = OCLMemorySpace.LOCAL;
                break;
            case CONSTANT:
                value = OCLMemorySpace.CONSTANT;
                break;
            case PRIVATE:
                value = OCLMemorySpace.PRIVATE;
                break;
            case STACK:
                value = OCLStack.STACK;
                break;
            case HEAP:
                value = OCLMemorySpace.HEAP;
                break;

        }

        guarantee(value != null, "unimplemented region: %s", region);
        gen.setResult(this, value);
    }

}
