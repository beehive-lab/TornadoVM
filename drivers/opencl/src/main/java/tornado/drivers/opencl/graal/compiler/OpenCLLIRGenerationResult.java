package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jdk.vm.ci.code.CallingConvention;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

public class OpenCLLIRGenerationResult extends LIRGenerationResult {

    private final Map<OCLKind, Set<Variable>> variableTable;

    public OpenCLLIRGenerationResult(
            String compilationUnitName,
            LIR lir,
            FrameMapBuilder frameMapBuilder,
            CallingConvention callingConvention) {
        super(compilationUnitName, lir, frameMapBuilder, callingConvention);
        variableTable = new HashMap<>();
    }

    public void insertVariable(Variable variable) {
        guarantee(variable.getPlatformKind() instanceof OCLKind, "invalid variable kind: %s", variable.getValueKind());
        OCLKind kind = (OCLKind) variable.getPlatformKind();

        variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(variable);
    }

    public Map<OCLKind, Set<Variable>> getVariableTable() {
        return variableTable;
    }

}
