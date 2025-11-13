package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFieldAddressArithmetic;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

import java.util.ArrayList;
import java.util.Optional;

public class SPIRVFieldCoopsAccess extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        ArrayList<TornadoAddressArithmeticNode> toBeDeleted = new ArrayList<>();
        for (SPIRVDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(SPIRVDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                SPIRVFieldAddressArithmetic spirvFieldAddressArithmetic = new SPIRVFieldAddressArithmetic(readDecompressedField);
                graph.addWithoutUnique(spirvFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(spirvFieldAddressArithmetic);
                toBeDeleted.add(tornadoAddressArithmeticNode);
            }
        }
        for (int i = 0; i < toBeDeleted.size(); i++) {
            toBeDeleted.get(i).safeDelete();
        }
    }

}
