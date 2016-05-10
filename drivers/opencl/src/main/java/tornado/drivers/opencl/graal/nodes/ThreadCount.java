package tornado.drivers.opencl.graal.nodes;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLUnary;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class ThreadCount extends FloatingNode implements LIRLowerable{
	public static final NodeClass<ThreadCount> TYPE = NodeClass.create(ThreadCount.class);
	
	@Input protected ConstantNode index;
	
	public ThreadCount(ConstantNode value) {
		 super(TYPE, StampFactory.forKind(Kind.Int));
	       assert stamp != null;
	       index = value;
	}

	@Override
	public void generate(NodeLIRBuilderTool gen) {
		gen.setResult(this, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.GLOBAL_SIZE, Kind.Int, (Value) index.asConstant()));
	}

}
