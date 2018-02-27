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
package tornado.drivers.opencl.graal.compiler.plugins;

import static uk.ac.manchester.tornado.common.Tornado.ENABLE_VECTORS;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.api.Vector;

public class OCLVectorNodePlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        if (!ENABLE_VECTORS) {
            return false;
        }

        if (type.getAnnotation(Vector.class) != null) {
            return createVectorInstance(b, type);
        }

        return false;

    }

    private boolean createVectorInstance(GraphBuilderContext b, ResolvedJavaType type) {
        OCLKind vectorKind = resolveOCLKind(type);
        if (vectorKind != OCLKind.ILLEGAL) {
            b.push(JavaKind.Object, b.append(new VectorValueNode(vectorKind)));
            return true;
        }

        return false;
    }

    private OCLKind resolveOCLKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            final HotSpotResolvedJavaType resolvedType = (HotSpotResolvedJavaType) type;
            return OCLKind.fromClass(resolvedType.mirror());
        }

        return OCLKind.ILLEGAL;
    }

}
