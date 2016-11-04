/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import tornado.drivers.opencl.graal.meta.OCLMemorySpace;
import tornado.drivers.opencl.graal.meta.OCLStack;

@NodeInfo
public class OCLMemoryRegion extends FloatingNode implements LIRLowerable{

    public static final NodeClass<OCLMemoryRegion> TYPE = NodeClass.create(OCLMemoryRegion.class);

    public static enum Region {
        GLOBAL, LOCAL, PRIVATE, CONSTANT, STACK, HEAP;
    }
    
    protected Region region;
    
    public OCLMemoryRegion(Region region){
        super(TYPE,StampFactory.objectNonNull());
        this.region = region;
    }
    
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value value = null;
        switch(region){
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
       
        guarantee(value != null, "unimplemented region: %s",region);
        gen.setResult(this, value);
    }

}
