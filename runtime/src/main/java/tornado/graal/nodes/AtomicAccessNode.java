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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;

@NodeInfo(shortName="Atomic")
public class AtomicAccessNode extends FloatingNode {
	

	public static final NodeClass<AtomicAccessNode>	TYPE	= NodeClass
																		.create(AtomicAccessNode.class);

	@Input(InputType.Association) protected ValueNode									value;

	public AtomicAccessNode(ValueNode value) {
		super(TYPE, value.stamp());
		assert stamp != null;
		this.value = value;
	}

	public ValueNode value() {
		return value;
	}

}