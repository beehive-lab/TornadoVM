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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.asm.OCLConstantValue;

@NodeInfo(nameTemplate = "{p#selection}")
public class VectorSelectionNode extends FloatingNode implements LIRLowerable {
    
    public static final NodeClass<VectorSelectionNode> TYPE = NodeClass.create(VectorSelectionNode.class);

    @Override
    public void generate(NodeLIRBuilderTool tool) {
        tool.setResult(this, new OCLConstantValue(selection.name().toLowerCase()));
    }
    
    public static enum VectorSelection {
        LO, Hi, ODD, EVEN;
    }
    
    private VectorSelection selection;
    
    public VectorSelectionNode(VectorSelection selection) {
        super(TYPE, StampFactory.forVoid());
        this.selection = selection;
    }
    
    public VectorSelection getSelection(){
        return selection;
    }
    
}
