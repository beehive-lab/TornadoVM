/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.nodes.vector;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
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
