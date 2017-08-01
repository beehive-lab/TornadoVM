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
package tornado.drivers.opencl.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;

@NodeInfo
public class LoadIndexedVectorNode extends LoadIndexedNode {

    public static final NodeClass<LoadIndexedVectorNode> TYPE = NodeClass.create(LoadIndexedVectorNode.class);

    public LoadIndexedVectorNode(OCLKind oclKind, ValueNode array, ValueNode index, JavaKind elementKind) {
        super(TYPE, OCLStampFactory.getStampFor(oclKind), array, index, elementKind);
    }

    @Override
    public boolean inferStamp() {
        return false;
    }

}
