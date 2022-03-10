/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.backend;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG_KERNEL_ARGS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEFAULT_HEAP_ALLOCATION;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_EXCEPTIONS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

import java.lang.reflect.Method;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.common.BackendDeopt;
import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLCodeProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMap;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLUtils;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLDataBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeMatchRules;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.directives.CompilerInternals;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class OCLBackend extends TornadoBackend<OCLProviders> implements FrameMap.ReferenceMapBuilderFactory {

    private static final String KERNEL_WARMUP = System.getProperty("tornado.fpga.kernel.warmup");
    private static boolean isFPGAInit = false;
    final OptionValues options;

    final OCLTargetDescription target;
    final OCLArchitecture architecture;
    final OCLDeviceContextInterface deviceContext;
    final OCLCodeProvider codeCache;
    final ScheduleMetaData scheduleMeta;
    OCLInstalledCode lookupCode;
    private boolean backEndInitialized;
    private boolean lookupCodeAvailable;

    public OCLBackend(OptionValues options, Providers providers, OCLTargetDescription target, OCLCodeProvider codeCache, OCLDeviceContextInterface deviceContext) {
        super(providers);
        this.options = options;
        this.target = target;
        this.codeCache = codeCache;
        this.deviceContext = deviceContext;
        architecture = (OCLArchitecture) target.arch;
        scheduleMeta = new ScheduleMetaData("oclbackend");

        if (KERNEL_WARMUP != null) {
            if (deviceContext.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR && !isFPGAInit) {
                initFPGA();
                isFPGAInit = true;
            }
        }
    }

    @SuppressWarnings("unused")
    private static Object lookupBufferAddress() {
        return CompilerInternals.getSlotsAddress();
    }

    public static boolean isDeviceAnFPGAAccelerator(OCLDeviceContextInterface deviceContext) {
        return deviceContext.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR;
    }

    @Override
    public OCLTargetDescription getTarget() {
        return target;
    }

    @Override
    public String decodeDeopt(long value) {
        return BackendDeopt.decodeDeopt(value, getProviders());
    }

    @Override
    public boolean isInitialised() {
        return backEndInitialized;
    }

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return new OCLReferenceMapBuilder();
    }

    private Method getLookupMethod() {
        Method method = null;
        try {
            method = this.getClass().getDeclaredMethod("lookupBufferAddress");
        } catch (NoSuchMethodException e) {
            Tornado.fatal("[FATAL ERROR] Unable to find the <lookupBufferAddress> method within OCLBackend");
        }
        return method;
    }

    public long readHeapBaseAddress(TaskMetaData meta) {
        final OCLByteBuffer parameters = deviceContext.getMemoryManager().getSubBuffer(0, 16);
        parameters.putLong(0);

        int task = lookupCode.executeTask(parameters, null, meta);
        lookupCode.readValue(parameters, meta, task);
        lookupCode.resolveEvent(parameters, meta, task);

        final long address = parameters.getLong(0);
        Tornado.info("Heap address @ 0x%x on %s ", address, deviceContext.getDevice().getDeviceName());
        return address;
    }

    /**
     * It allocates the smallest of the requested heap size or the max global memory
     * size.
     */
    @Override
    public void allocateHeapMemoryOnDevice() {
        long memorySize = Math.min(DEFAULT_HEAP_ALLOCATION, deviceContext.getDevice().getDeviceMaxAllocationSize());
        if (memorySize < DEFAULT_HEAP_ALLOCATION) {
            Tornado.info("Unable to allocate %s of heap space - resized to %s", humanReadableByteCount(DEFAULT_HEAP_ALLOCATION, false), humanReadableByteCount(memorySize, false));
        }
        Tornado.info("%s: allocating %s of heap space", deviceContext.getDevice().getDeviceName(), humanReadableByteCount(memorySize, false));
        deviceContext.getMemoryManager().allocateDeviceMemoryRegions(memorySize);
    }

    private String getDriverAndDevice(TaskMetaData task, int[] deviceInfo) {
        return task.getId() + ".device=" + deviceInfo[0] + ":" + deviceInfo[1];
    }

    /**
     * We explore all devices in driver 0;
     *
     * @return int[]
     */
    public int[] getDriverAndDevice() {
        int numDev = TornadoCoreRuntime.getTornadoRuntime().getDriver(OCLDriver.class).getDeviceCount();
        int deviceIndex = 0;
        for (int i = 0; i < numDev; i++) {
            TornadoAcceleratorDevice device = TornadoCoreRuntime.getTornadoRuntime().getDriver(OCLDriver.class).getDevice(i);
            OCLTargetDevice dev = (OCLTargetDevice) device.getPhysicalDevice();
            if (dev == deviceContext.getDevice()) {
                deviceIndex = i;
            }
        }
        int driverIndex = TornadoCoreRuntime.getTornadoRuntime().getDriverIndex(OCLDriver.class);
        return new int[] { driverIndex, deviceIndex };
    }

    private boolean isJITCompilationForFPGAs(String deviceFullName) {
        String deviceDriver = deviceFullName.split("=")[1];
        int driverIndex = Integer.parseInt(deviceDriver.split(":")[0]);
        int deviceIndex = Integer.parseInt(deviceDriver.split(":")[1]);
        TornadoDevice device = getTornadoRuntime().getDriver(driverIndex).getDevice(deviceIndex);
        String platformName = device.getPlatformName();
        if (!isDeviceAnFPGAAccelerator(deviceContext) || !isFPGA(platformName)) {
            return false;
        } else {
            return isDeviceAnFPGAAccelerator(deviceContext) && (isFPGA(platformName));
        }
    }

    private boolean isFPGA(String platformName) {
        return ((platformName.contains("FPGA") || platformName.contains("Xilinx")));
    }

    /*
     * Retrieve the address of the heap on the device
     */
    public TaskMetaData compileLookupBufferKernel() {

        TaskMetaData meta = new TaskMetaData(scheduleMeta, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
        meta.setDevice(deviceContext.asMapping());

        OCLCodeCache codeCache = deviceContext.getCodeCache();
        int[] deviceInfo = getDriverAndDevice();
        String deviceFullName = getDriverAndDevice(meta, deviceInfo);

        if (deviceContext.isCached("internal", OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME)) {
            // Option 1) Getting the lookupBufferAddress from the cache
            lookupCode = deviceContext.getInstalledCode("internal", OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
            if (lookupCode != null) {
                lookupCodeAvailable = true;
            }
        } else if (codeCache.isLoadBinaryOptionEnabled() && codeCache.getOpenCLBinary(deviceFullName) != null) {

            // Option 2) Loading pre-compiled lookupBufferAddress kernel FPGA
            Path lookupPath = Paths.get(codeCache.getOpenCLBinary(deviceFullName));
            lookupCode = codeCache.installEntryPointForBinaryForFPGAs(meta.getId(), lookupPath, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
            if (lookupCode != null) {
                lookupCodeAvailable = true;
            }
        } else {
            // Option 3) JIT Compilation of the lookupBufferAddress kernel
            // Avoid JIT compilation for FPGAs due to unsupported feature
            ResolvedJavaMethod resolveMethod = getTornadoRuntime().resolveMethod(getLookupMethod());
            OCLProviders providers = (OCLProviders) getProviders();
            OCLCompilationResult result = OCLCompiler.compileCodeForDevice(resolveMethod, null, meta, providers, this);

            if (VIRTUAL_DEVICE_ENABLED) {
                RuntimeUtilities.maybePrintSource(result.getTargetCode());
            }

            boolean isCompilationForFPGAs = isJITCompilationForFPGAs(deviceFullName);

            if (isDeviceAnFPGAAccelerator(deviceContext)) {
                lookupCode = deviceContext.installCode(result.getId(), result.getName(), result.getTargetCode(), false);
            } else {
                lookupCode = deviceContext.installCode(result);
            }

            if (deviceContext.isKernelAvailable() && !isCompilationForFPGAs) {
                lookupCodeAvailable = true;
            }
        }
        return meta;
    }

    public void runLookUpBufferAddressKernel() {
        TaskMetaData meta = new TaskMetaData(scheduleMeta, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME, 0);
        OCLCodeCache codeCache = deviceContext.getCodeCache();
        if (deviceContext.getInstalledCode("internal", OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME) != null) {
            lookupCode = deviceContext.getInstalledCode("internal", OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
        } else {
            lookupCode = codeCache.installEntryPointForBinaryForFPGAs(meta.getId(), Paths.get(OCLCodeCache.fpgaBinLocation), OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
        }

        if (lookupCode != null) {
            lookupCodeAvailable = true;
            runAndReadLookUpKernel(meta);
        }
    }

    public boolean isLookupCodeAvailable() {
        return lookupCodeAvailable;
    }

    private void runAndReadLookUpKernel(TaskMetaData meta) {
        deviceContext.getMemoryManager().init(this, readHeapBaseAddress(meta));
    }

    private void initFPGA() {
        OCLCodeCache check = deviceContext.getCodeCache();
        try {
            Path lookupPath = Paths.get(KERNEL_WARMUP);
            check.installEntryPointForBinaryForFPGAs("oclbackend", lookupPath, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
        } catch (InvalidPathException e) {
            throw new TornadoRuntimeException(e);
        }
    }

    /**
     * JIT compilation of the Look Up Buffer Address into the FPGA
     *
     * @return {@link TaskMetaData}
     */
    private TaskMetaData fpgaInstallCodeLookUpBuffer() {
        TaskMetaData meta = new TaskMetaData(scheduleMeta, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
        ResolvedJavaMethod resolveMethod = getTornadoRuntime().resolveMethod(getLookupMethod());
        OCLProviders providers = (OCLProviders) getProviders();
        OCLCompilationResult result = OCLCompiler.compileCodeForDevice(resolveMethod, null, meta, providers, this);
        lookupCode = deviceContext.installCode(result.getId(), result.getName(), result.getTargetCode(), false);
        return meta;
    }

    @Override
    public void init() {
        if (VIRTUAL_DEVICE_ENABLED) {
            compileLookupBufferKernel();
            backEndInitialized = true;
            return;
        }

        allocateHeapMemoryOnDevice();
        TaskMetaData meta = compileLookupBufferKernel();
        if (isLookupCodeAvailable()) {
            // Only run kernel is the compilation was correct.
            runAndReadLookUpKernel(meta);
        }
        backEndInitialized = true;
    }

    @Override
    public int getMethodIndex() {
        return 0;
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    protected OCLAssembler createAssembler() {
        return new OCLAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {
        emitCode((OCLCompilationResultBuilder) crb, lir, method);
    }

    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        unimplemented();
        return null;
    }

    public void emitCode(OCLCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {
        final OCLAssembler asm = (OCLAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);
    }

    private void emitEpilogue(OCLAssembler asm) {
        asm.endScope(" kernel");
    }

    private void addVariableDef(Map<OCLKind, Set<Variable>> kindToVariable, Variable value) {
        if (value != null) {

            if (!(value.getPlatformKind() instanceof OCLKind)) {
                shouldNotReachHere();
            }

            OCLKind oclKind = (OCLKind) value.getPlatformKind();
            if (oclKind == OCLKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(oclKind)) {
                kindToVariable.put(oclKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(oclKind);
            varList.add(value);
        }
    }

    private void emitVariableDefs(OCLCompilationResultBuilder crb, OCLAssembler asm, LIR lir) {
        Map<OCLKind, Set<Variable>> kindToVariable = new HashMap<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        for (AbstractBlockBase<?> b : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(b)) {

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof Variable) {
                        Variable variable = (Variable) value;
                        if (variable.getName() != null) {
                            addVariableDef(kindToVariable, variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        Logger.traceCodeGen(Logger.BACKEND.OpenCL, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);

        for (OCLKind type : kindToVariable.keySet()) {
            asm.indent();
            asm.emit("%s ", type);
            for (Variable var : kindToVariable.get(type)) {
                asm.emitValue(crb, var);
                asm.emit(", ");
            }
            asm.emitByte(';', asm.position() - 2);
            asm.eol();
        }

    }

    private void emitDebugKernelArgs(OCLAssembler asm, ResolvedJavaMethod method) {
        asm.emitLine("if(get_global_id(0) == 0 && get_global_id(1) ==0){");
        asm.pushIndent();
        asm.emitStmt("int numArgs = slots[5] >> 32");
        asm.emitStmt("printf(\"got %%d args...\\n\",numArgs)");
        asm.emitLine("for(int i=0;i<numArgs;i++) {");
        asm.pushIndent();
        asm.emitStmt("printf(\"%20s - arg[%%d]: 0x%%lx\\n\", i, slots[6 + i])", method.getName());
        asm.popIndent();
        asm.emitLine("}");
        asm.popIndent();
        asm.emitLine("}");
    }

    private void emitPrologue(OCLCompilationResultBuilder crb, OCLAssembler asm, ResolvedJavaMethod method, LIR lir) {

        String methodName = crb.compilationResult.getName();

        if (crb.isKernel()) {
            /*
             * BUG There is a bug on some OpenCL devices which requires us to insert an
             * extra OpenCL buffer into the kernel arguments. This has the effect of
             * shifting the devices address mappings, which allows us to avoid the heap
             * starting at address 0x0. (I assume that this is an interesting case that
             * leads to a few issues.) Iris Pro is the only culprit at the moment.
             */
            final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
            if (cfg.getStartBlock().getEndNode().predecessor().asNode() instanceof FPGAWorkGroupSizeNode) {
                FPGAWorkGroupSizeNode fpgaNode = (FPGAWorkGroupSizeNode) (cfg.getStartBlock().getEndNode().predecessor().asNode());
                String attribute = fpgaNode.createThreadAttribute();

                asm.emitSymbol(attribute);
                asm.emitLine("");
            }

            final String bumpBuffer = (deviceContext.needsBump()) ? String.format("%s void *dummy, ", OCLAssemblerConstants.GLOBAL_MEM_MODIFIER) : "";

            asm.emitLine("%s void %s(%s%s)", OCLAssemblerConstants.KERNEL_MODIFIER, methodName, bumpBuffer, architecture.getABI());
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
            asm.emitStmt("%s ulong *%s = (%s ulong *) &%s[%s]", OCLAssemblerConstants.GLOBAL_MEM_MODIFIER, OCLAssemblerConstants.FRAME_REF_NAME, OCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OCLAssemblerConstants.HEAP_REF_NAME, OCLAssemblerConstants.FRAME_BASE_NAME);
            asm.eol();

            if (DEBUG_KERNEL_ARGS && (method != null && !method.getDeclaringClass().getUnqualifiedName().equalsIgnoreCase(this.getClass().getSimpleName()))) {
                emitDebugKernelArgs(asm, method);
            }

            if (ENABLE_EXCEPTIONS) {
                asm.emitStmt("if(slots[0] != 0) return");
            }
            asm.eol();
        } else {

            final CallingConvention incomingArguments = CodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method, false);
            methodName = OCLUtils.makeMethodName(method);

            final JavaKind returnKind = method.getSignature().getReturnKind();
            String returnStr = "<unknown>";
            if (returnKind == JavaKind.Void) {
                returnStr = "void";
            } else {
                final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
                OCLKind returnOclKind = (returnType.getAnnotation(Vector.class) == null) ? getTarget().getOCLKind(returnKind) : OCLKind.fromResolvedJavaType(returnType);
                returnStr = returnOclKind.toString();
            }
            // getTarget().getLIRKind(returnKind);
            asm.emit("%s %s(%s", returnStr, methodName, architecture.getABI());

            final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
            final Value[] params = new Value[incomingArguments.getArgumentCount()];

            if (params.length > 0) {
                asm.emit(", ");
            }

            for (int i = 0; i < params.length; i++) {
                final AllocatableValue param = incomingArguments.getArgument(i);
                OCLKind oclKind = (OCLKind) param.getPlatformKind();
                if (locals[i].getType().getJavaKind().isObject()) {
                    OCLKind tmpKind = OCLKind.resolveToVectorKind(locals[i].getType().resolve(method.getDeclaringClass()));
                    if (tmpKind != OCLKind.ILLEGAL) {
                        oclKind = tmpKind;
                    }
                }
                guarantee(oclKind != OCLKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit("%s %s", oclKind.toString(), locals[i].getName());
                if (i < params.length - 1) {
                    asm.emit(", ");
                }
            }
            asm.emit(")");
            asm.eol();
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
        }
    }

    @Override
    public OCLSuitesProvider getTornadoSuites() {
        return ((OCLProviders) getProviders()).getSuitesProvider();
    }

    public OCLCompilationResultBuilder newCompilationResultBuilder(FrameMap frameMap, OCLCompilationResult compilationResult, CompilationResultBuilderFactory factory, boolean isKernel,
            boolean isParallel) {

        OCLAssembler asm = createAssembler();
        OCLFrameContext frameContext = new OCLFrameContext();
        DataBuilder dataBuilder = new OCLDataBuilder();
        OCLCompilationResultBuilder crb = new OCLCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new OCLFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new OCLFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new OCLLIRGenerationResult(identifier, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenResult) {
        return new OCLLIRGenerator(getProviders(), lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new OCLNodeLIRBuilder(graph, lirGen, new OCLNodeMatchRules(lirGen));
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public OCLCodeProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in OCLBackend not implemented yet.");
        return null;
    }

    public void reset() {
        getDeviceContext().reset();
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationRequest cr, CompilationResult cr1, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in OCLBackend not implemented yet.");
        return null;
    }
}
