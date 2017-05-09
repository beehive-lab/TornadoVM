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
package tornado.graal.nodes;

import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.ValueNode;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public class TornadoDirectCallTargetNode extends DirectCallTargetNode {

    public static final NodeClass<TornadoDirectCallTargetNode> TYPE = NodeClass.create(TornadoDirectCallTargetNode.class);

    public TornadoDirectCallTargetNode(ValueNode[] arguments, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod target, Type callType, InvokeKind invokeKind) {
        super(TYPE, arguments, returnStamp, signature, target, callType, invokeKind);
    }
}
