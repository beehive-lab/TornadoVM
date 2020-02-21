package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;

import java.util.HashSet;
import java.util.Set;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

public class PTXCompilationResultBuilder extends CompilationResultBuilder {
    private boolean isKernel;
    private boolean isParallel;
    private Set<ResolvedJavaMethod> nonInlinedMethods;

    public PTXCompilationResultBuilder(CodeCacheProvider codeCache,
                                       ForeignCallsProvider foreignCalls,
                                       FrameMap frameMap,
                                       Assembler asm,
                                       DataBuilder dataBuilder,
                                       FrameContext frameContext,
                                       OptionValues options,
                                       CompilationResult compilationResult) {
        super(codeCache,
                foreignCalls,
                frameMap,
                asm,
                dataBuilder,
                frameContext,
                options,
                getDebugContext(),
                compilationResult,
                Register.None
        );

        nonInlinedMethods = new HashSet<>();
    }

    public PTXAssembler getAssembler() {
        return (PTXAssembler) asm;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public void setParallel(boolean value) {
        isParallel = value;
    }

    public boolean getParallel() {
        return isParallel;
    }

    public void addNonInLinedMethod(ResolvedJavaMethod method) {
        nonInlinedMethods.add(method);
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }
}
