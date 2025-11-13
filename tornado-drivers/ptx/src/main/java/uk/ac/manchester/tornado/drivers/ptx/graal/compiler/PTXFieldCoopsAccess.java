package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXDecompressedReadFieldNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFieldAddressArithmetic;
import uk.ac.manchester.tornado.runtime.graal.nodes.calc.TornadoAddressArithmeticNode;

import java.util.ArrayList;
import java.util.Optional;

public class PTXFieldCoopsAccess extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        ArrayList<TornadoAddressArithmeticNode> toBeDeleted = new ArrayList<>();
        for (PTXDecompressedReadFieldNode readDecompressedField : graph.getNodes().filter(PTXDecompressedReadFieldNode.class)) {
            for (TornadoAddressArithmeticNode tornadoAddressArithmeticNode : readDecompressedField.usages().filter(TornadoAddressArithmeticNode.class)) {
                PTXFieldAddressArithmetic ptxFieldAddressArithmetic = new PTXFieldAddressArithmetic(readDecompressedField);
                graph.addWithoutUnique(ptxFieldAddressArithmetic);
                tornadoAddressArithmeticNode.replaceAtUsages(ptxFieldAddressArithmetic);
                toBeDeleted.add(tornadoAddressArithmeticNode);
            }
        }
        for (int i = 0; i < toBeDeleted.size(); i++) {
            toBeDeleted.get(i).safeDelete();
        }
    }

}
