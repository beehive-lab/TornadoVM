package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVIRGenerationResult extends LIRGenerationResult {

    private final Map<SPIRVKind, Set<Variable>> variableTable;

    public SPIRVIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig,
            CallingConvention callingConvention) {
        super(compilationId, lir, frameMapBuilder, registerAllocationConfig, callingConvention);
        variableTable = new HashMap<>();
    }

    public void insertVariable(Variable variable) {
        guarantee(variable.getPlatformKind() instanceof SPIRVKind, "invalid variable kind: %s", variable.getValueKind());
        SPIRVKind kind = (SPIRVKind) variable.getPlatformKind();
        variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(variable);
    }

    public Map<SPIRVKind, Set<Variable>> getVariableTable() {
        return variableTable;
    }
}
