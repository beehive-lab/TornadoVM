package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLAddressNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFieldAddressArithmetic;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

import java.util.ArrayList;
import java.util.Optional;

public class OCLFieldCoopsAccess extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        ArrayList<TornadoAddressArithmeticNode> toBeDeleted = new ArrayList<>();
        for (OCLDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(OCLDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                OCLFieldAddressArithmetic oclFieldAddressArithmetic = new OCLFieldAddressArithmetic(readDecompressedField);
                graph.addWithoutUnique(oclFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(oclFieldAddressArithmetic);
                toBeDeleted.add(tornadoAddressArithmeticNode);
            }
        }
        for (int i = 0; i < toBeDeleted.size(); i++) {
            toBeDeleted.get(i).safeDelete();
        }
    }

}
