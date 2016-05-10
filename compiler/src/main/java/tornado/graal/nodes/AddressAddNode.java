package tornado.graal.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.AddLocationNode;
import com.oracle.graal.nodes.extended.LocationNode;
import com.oracle.graal.nodes.memory.ReadNode;

@NodeInfo
public class AddressAddNode extends FixedWithNextNode implements Canonicalizable {

	public static final NodeClass<AddressAddNode>	TYPE	= NodeClass.create(AddressAddNode.class);

	@Input private ValueNode reference;
	@Input private LocationNode value;
	
	public AddressAddNode(ValueNode reference, LocationNode value) {
		super(TYPE, StampFactory.forVoid());
		this.reference = reference;
		this.value = value;
	}

	public ValueNode getReference() {
		return reference;
	}

	@Override
	public Node canonical(CanonicalizerTool arg0) {
		if(reference instanceof ReadNode){
			System.out.printf("canonical: simplyfying node %s\n",toString());
		
			final ReadNode oldObject = (ReadNode) reference;
			final AddLocationNode addLocation = new AddLocationNode(oldObject.accessLocation(),value);
			final ReadNode object = new ReadNode(oldObject.object(),addLocation, (ValueNode) oldObject.getGuard(),oldObject.getBarrierType());
			
		
			graph().addOrUnique(addLocation);
			
			return object;
		}
		return this;
	}


}
