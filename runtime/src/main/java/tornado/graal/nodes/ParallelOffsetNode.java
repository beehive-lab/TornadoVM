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

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo(nameTemplate = "Offset")
public class ParallelOffsetNode extends AbstractParallelNode {

    public static final NodeClass<ParallelOffsetNode> TYPE = NodeClass
            .create(ParallelOffsetNode.class);

    public ParallelOffsetNode(int index, ValueNode offset) {
        super(TYPE, index, offset);
    }

}
