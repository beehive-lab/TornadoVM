package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLDecompressedReadFieldNode;

@NodeInfo
public class OCLFieldAddressArithmetic extends FloatingNode implements LIRLowerable {
    public static final NodeClass<OCLFieldAddressArithmetic> TYPE = NodeClass.create(OCLFieldAddressArithmetic.class);

    @Input
    protected OCLDecompressedReadFieldNode base;

    public OCLFieldAddressArithmetic(OCLDecompressedReadFieldNode base) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.base = base;
    }

    public void generate(NodeLIRBuilderTool generator) {
        Value decompressedAddress = generator.operand(base);
        generator.setResult(this, decompressedAddress);
    }
}
