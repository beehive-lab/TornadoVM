package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

@NodeInfo
public class LoadIndexedVectorNode extends LoadIndexedNode {

    public static final NodeClass<LoadIndexedVectorNode> TYPE = NodeClass.create(LoadIndexedVectorNode.class);
    private final SPIRVKind spirvKind;

    public LoadIndexedVectorNode(SPIRVKind kind, ValueNode array, ValueNode index, JavaKind elementKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(kind), array, index, null, elementKind);
        this.spirvKind = kind;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(SPIRVStampFactory.getStampFor(spirvKind));
    }

    public SPIRVKind getSPIRVKind() {
        return spirvKind;
    }
}
