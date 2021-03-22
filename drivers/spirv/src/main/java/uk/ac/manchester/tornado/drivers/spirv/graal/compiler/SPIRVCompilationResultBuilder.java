package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVCodeProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;

import java.util.HashSet;
import java.util.Set;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

public class SPIRVCompilationResultBuilder extends CompilationResultBuilder {

    private final Set<ResolvedJavaMethod> nonInlinedMethods;
    private boolean isKernel;
    private int loops = 0;
    private boolean isParallel;
    private SPIRVDeviceContext deviceContext;

    public SPIRVCompilationResultBuilder(SPIRVCodeProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, SPIRVAssembler asm, DataBuilder dataBuilder, SPIRVFrameContext frameContext,
            OptionValues options, SPIRVCompilationResult compilationResult) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, Register.None);
        nonInlinedMethods = new HashSet<>();
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setKernel(boolean isKernel) {
        this.isKernel = isKernel;
    }

    public void setParallel(boolean isParallel) {
        this.isParallel = isParallel;

    }

    public void setDeviceContext(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }
}
