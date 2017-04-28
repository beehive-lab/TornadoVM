/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.api.Vector;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;

import static tornado.common.Tornado.ENABLE_VECTORS;

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
            b.push(JavaKind.Object, b.recursiveAppend(new VectorValueNode(vectorKind)));
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
