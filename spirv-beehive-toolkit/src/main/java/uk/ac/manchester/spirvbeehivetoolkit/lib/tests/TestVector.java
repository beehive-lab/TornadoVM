package uk.ac.manchester.spirvbeehivetoolkit.lib.tests;

import uk.ac.manchester.spirvbeehivetoolkit.lib.InvalidSPIRVModuleException;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVHeader;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpBitcast;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpBranch;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpBranchConditional;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpCompositeExtract;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpIEqual;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSConvert;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSLessThan;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSource;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeBool;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpUConvert;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVBuiltIn;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLinkageType;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVSourceLanguage;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVStorageClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class TestVector {

    private static void writeBufferToFile(ByteBuffer buffer, String filepath) {
        buffer.flip();
        File out = new File(filepath);
        try {
            FileChannel channel = new FileOutputStream(out, false).getChannel();
            channel.write(buffer);
            channel.close();
        } catch (IOException e) {
            System.err.println("IO exception: " + e.getMessage());
        }

    }

    public static void writeModuleToFile(SPIRVModule module, String filepath) {
        ByteBuffer out = ByteBuffer.allocate(module.getByteCount());
        out.order(ByteOrder.LITTLE_ENDIAN);
        module.close().write(out);
        writeBufferToFile(out, filepath);
    }

    /**
     * SPIR-V CODE for the following OpenCL kernel:
     *
     * <code>
     *     __kernel void testVectorInit(__global int* a) {
     *          int idx = get_global_id(0);
     * 	        a[idx] = 50;
     *     }
     * </code>
     *
     */
    public static void testVectorInit()  {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                    1,
                    2,
                    29,
                    0,
                    0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));         // Uses OpTypeInt to declare 64-bit integer types

        // For Double support: Float64 capability
        // module.add(new SPIRVOpCapability(SPIRVCapability.Float64()));

        // Extension for Import "OpenCL.std"
        SPIRVId idExtension = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idExtension, new SPIRVLiteralString("OpenCL.std")));

        // OpenCL Version Set
        module.add(new SPIRVOpSource(SPIRVSourceLanguage.OpenCL_C(), new SPIRVLiteralInteger(100000), new SPIRVOptionalOperand<>(), new SPIRVOptionalOperand<>()));

        // Indicates a 64-bit module, where the address width is equal to 64 bits.
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        // OpName: Assign a name string to another instruction’s Result <id>. This has no semantic impact and can safely be removed from a module.
        SPIRVId idName = module.getNextId();
        module.add(new SPIRVOpName(idName, new SPIRVLiteralString("__spirv_BuiltInGlobalInvocationId")));

        SPIRVId aName = module.getNextId();
        module.add(new SPIRVOpName(aName, new SPIRVLiteralString("a")));

        SPIRVId aAddrName = module.getNextId();
        module.add(new SPIRVOpName(aAddrName, new SPIRVLiteralString("a.addr")));

        // Decorates
        // A) Global ID invocation
        module.add(new SPIRVOpDecorate(idName, SPIRVDecoration.BuiltIn(SPIRVBuiltIn.GlobalInvocationId())));

        // B) Constant
        module.add(new SPIRVOpDecorate(idName, SPIRVDecoration.Constant()));

        // C) LinkageAttributes
        SPIRVLiteralString literalGlobalID = new SPIRVLiteralString("__spirv_BuiltInGlobalInvocationId");
        module.add(new SPIRVOpDecorate(idName, SPIRVDecoration.LinkageAttributes(literalGlobalID, SPIRVLinkageType.Import())));

        // Int 32
        SPIRVId uint = module.getNextId();
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // Int 64
        SPIRVId ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));

        // Type Vector: 3 elements of type long
        SPIRVId v3ulong = module.getNextId();
        module.add(new SPIRVOpTypeVector(v3ulong, ulong, new SPIRVLiteralInteger(3)));

        // OpConstants
        // Type, result, value
        SPIRVId constant50 = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant50, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        // Type pointer
        SPIRVId pointerResult = module.getNextId();
        // STORAGE CLASS <Input>: from pipeline. Visible across all functions in the current invocation. Variables declared with this
        // storage class are read-only, and must not have initializers.
        module.add(new SPIRVOpTypePointer(pointerResult, SPIRVStorageClass.Input(), v3ulong));

        // Type Void
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        // STORAGE CLASS <CrossWorkGroup> Visible across all functions of all invocations of all work groups. OpenCL global memory.
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        // Function declaration
        SPIRVId mainFunctionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(mainFunctionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossWorkGroupUInt)));

        SPIRVId ptrFunctionPtrCrossWorkGroup = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPtrCrossWorkGroup, SPIRVStorageClass.Function(), uint));

        SPIRVId functionTypePtr = module.getNextId();
        module.add(new SPIRVOpTypePointer(functionTypePtr, SPIRVStorageClass.Function(), ptrCrossWorkGroupUInt));

        SPIRVId pointer = module.getNextId();
        module.add(new SPIRVOpTypePointer(pointer, SPIRVStorageClass.Input(), v3ulong));
        module.add(new SPIRVOpVariable(pointer, idName, SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));

        SPIRVId functionDef = module.getNextId();
        functionScope = module.add(new SPIRVOpFunction(voidType, functionDef, SPIRVFunctionControl.DontInline(), mainFunctionPre));
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossWorkGroupUInt, aName));

        // Entry point is define at the module level, not at the function level
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                functionDef,
                new SPIRVLiteralString("testVectorInit"),
                new SPIRVMultipleOperands<>(idName)
        ));

        SPIRVOpLabel entryLabel = new SPIRVOpLabel(module.getNextId());
        blockScope = functionScope.add(entryLabel);

        blockScope.add(new SPIRVOpVariable(
                functionTypePtr,
                aAddrName,
                SPIRVStorageClass.Function(),
                new SPIRVOptionalOperand<>()
        ));
        SPIRVId idx = module.getNextId();
        blockScope.add(new SPIRVOpVariable(
                ptrFunctionPtrCrossWorkGroup,
                idx,
                SPIRVStorageClass.Function(),
                new SPIRVOptionalOperand<>()
        ));

        blockScope.add(new SPIRVOpStore(
                aAddrName,
                aName,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId load17 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                v3ulong,
                load17,
                idName,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(32)))
        ));

        SPIRVId call = module.getNextId();
        blockScope.add(new SPIRVOpCompositeExtract(
                ulong,
                call,
                load17,
                new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(0))
        ));

        SPIRVId conv = module.getNextId();
        blockScope.add(new SPIRVOpUConvert(uint, conv, call));

        blockScope.add(new SPIRVOpStore(
                idx,
                conv,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId load20 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossWorkGroupUInt,
                load20,
                aAddrName,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId load21 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                load21,
                idx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId idxprom = module.getNextId();
        blockScope.add(new SPIRVOpUConvert(ulong, idxprom, load21));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUInt,
                ptridx,
                load20,
                idxprom,
                new SPIRVMultipleOperands()
        ));

        blockScope.add(new SPIRVOpStore(
                ptridx,
                constant50,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV.spv");
    }


    /**
     * SPIR-V code for the following OpenCL snippet:
     *
     * <code>
     *     __kernel void vectorCopy(__global int* a, global int *b) {
     * 	        int idx = get_global_id(0);
     * 	        a[idx] = b[idx];
     *      }
     * </code>
     */
    public static void testVectorCopy() {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                        1,
                        2,
                        29,    // First ID available for SPIR-V
                        0,
                        0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));         // Uses OpTypeInt to declare 64-bit integer types

        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));

        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        SPIRVId idSPIRVBuiltin = module.getNextId();

        // Add module entry function
        SPIRVId mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                mainFunctionID,
                new SPIRVLiteralString("testVectorCopy"),
                new SPIRVMultipleOperands<>(idSPIRVBuiltin)
        ));


        // OpSource
        module.add(new SPIRVOpSource(
                SPIRVSourceLanguage.OpenCL_C(),
                new SPIRVLiteralInteger(100000),
                new SPIRVOptionalOperand<>(),
                new SPIRVOptionalOperand<>()));

        // Decoration
        // Auxiliary information such as built-in variable, stream numbers, invariance, interpolation type,
        // relaxed precision, etc., added to <id>s or structure-type members through Decorations.

        // Add Decorators for the GetGlobalID intrinsics
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(SPIRVBuiltIn.GlobalInvocationId())));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString("spirv_BuiltInGlobalInvocationId"), SPIRVLinkageType.Import())));

        // Decorates for alignment of data
        SPIRVId idx = module.getNextId();
        SPIRVId a_addr = module.getNextId();
        SPIRVId b_addr = module.getNextId();
        module.add(new SPIRVOpDecorate(idx, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(4))));
        module.add(new SPIRVOpDecorate(a_addr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(b_addr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Type declaration
        SPIRVId ulong = module.getNextId();
        SPIRVId uint = module.getNextId();
        SPIRVId v3ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));
        // Vector of 3 longs
        module.add(new SPIRVOpTypeVector(v3ulong, ulong, new SPIRVLiteralInteger(3)));

        // Type pointers
        // OpPointerType: Declare a new pointer type.
        // Storage Class is the Storage Class of the memory holding the object pointed to. If there was a
        // forward reference to this type from an OpTypeForwardPointer, the Storage Class of that instruction
        // must equal the Storage Class of this instruction.
        // pointer to the vector ulong
        SPIRVId ptrInputv3ulong = module.getNextId();
        // INPUT Class: Input from pipeline. Visible across all functions in the current invocation. Variables
        // declared with this storage class are read-only, and must not have initializers.
        module.add(new SPIRVOpTypePointer(ptrInputv3ulong, SPIRVStorageClass.Input(), v3ulong));

        // Type void
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        // pointer to a crossGroup of uint
        SPIRVId ptrCrossGroupUint = module.getNextId();
        // CrossWorkGroup: Shared across all invocations within a work group. Visible across all functions.
        // The OpenGL "shared" storage qualifier. OpenCL local memory.
        module.add(new SPIRVOpTypePointer(ptrCrossGroupUint, SPIRVStorageClass.CrossWorkgroup(), uint));

        // Pointer to a function
        // %9 = OpTypeFunction %void %_ptr_CrossWorkgroup_uint %_ptr_CrossWorkgroup_uint
        SPIRVId mainFunctionPre = module.getNextId();

        // Declaration of the function with all parameter types
        module.add(new SPIRVOpTypeFunction(mainFunctionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossGroupUint, ptrCrossGroupUint)));

        // ptrFunctionToPtrCrossWorkGroup ---> I dont get why we need this
        SPIRVId ptrFunctionToPtrCrossWorkGroup = module.getNextId();
        // FUNCTION: Visible only within the declaring function of the current invocation. Regular function memory.
        module.add(new SPIRVOpTypePointer(ptrFunctionToPtrCrossWorkGroup, SPIRVStorageClass.Function(), ptrCrossGroupUint));

        // Pointer to a function of type uint
        SPIRVId ptrFunctionUint = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUint, SPIRVStorageClass.Function(), uint));

        // Variable declaration
        module.add(new SPIRVOpVariable(ptrInputv3ulong, idSPIRVBuiltin, SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));

        // Function Declaration
        functionScope = module.add(new SPIRVOpFunction(voidType, mainFunctionID, SPIRVFunctionControl.DontInline(), mainFunctionPre));

        SPIRVId a = module.getNextId();
        SPIRVId b = module.getNextId();

        // Parameter Declaration
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossGroupUint, a));   // Buffer A
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossGroupUint, b));   // Buffer B

        SPIRVOpLabel entry = new SPIRVOpLabel(module.getNextId());
        blockScope = functionScope.add(entry);

        blockScope.add(new SPIRVOpVariable(ptrFunctionToPtrCrossWorkGroup, a_addr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionToPtrCrossWorkGroup, b_addr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUint, idx, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(
                a_addr,
                a,
                new SPIRVOptionalOperand<>(
                        SPIRVMemoryAccess.Aligned(
                                new SPIRVLiteralInteger(8)))
        ));

        blockScope.add(new SPIRVOpStore(
                b_addr,
                b,
                new SPIRVOptionalOperand<>(
                        SPIRVMemoryAccess.Aligned(
                                new SPIRVLiteralInteger(8)))
        ));

        // Call Thread-ID getGlobalId(0)
        SPIRVId id19 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                v3ulong,
                id19,
                idSPIRVBuiltin,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(32)))
        ));

        // Intrinsic call
        SPIRVId call = module.getNextId();
        blockScope.add(new SPIRVOpCompositeExtract(ulong, call, id19, new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(0))));

        SPIRVId conv = module.getNextId();
        // SPIRVOpUConvert: Convert unsigned width. This is either a truncate or a zero extend.
        blockScope.add(new SPIRVOpUConvert(uint, conv, call));

        // Store the globalIdx into idx variable
        blockScope.add(new SPIRVOpStore(idx, conv,new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        // Load A[i] and b[i] using the thread index stored in idx
        SPIRVId id22 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossGroupUint,
                id22,
                b_addr,    // Load B[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id23 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id23,
                idx,    // Load B[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));
        SPIRVId idxprom = module.getNextId();
        // SPIRVOpSConvert: Convert signed width. This is either a truncate or a sign extend.
        blockScope.add(new SPIRVOpSConvert(ulong, idxprom, id23));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossGroupUint,
                ptridx,
                id22,
                idxprom,
                new SPIRVMultipleOperands()
        ));

        SPIRVId id26 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id26,
                ptridx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId id27 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossGroupUint,
                id27,
                a_addr,    // Load A[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id28 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id28,
                idx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId idxprom1 = module.getNextId();
        blockScope.add(new SPIRVOpSConvert(ulong, idxprom1, id28));

        SPIRVId ptridx2 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossGroupUint,
                ptridx2,
                id27,
                idxprom1,
                new SPIRVMultipleOperands()
        ));

        blockScope.add(new SPIRVOpStore(
                ptridx2,
                id26,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV2.spv");
    }

    /**
     * Test for Vector Addition:
     *
     * <code>
     *      __kernel void vectorAdd(__global int* a, __global int *b, __global int *c) {
     * 	        int idx = get_global_id(0);
     * 	        a[idx] = b[idx] + c[idx];
     *      }
     * </code>
     *
     */
    public static void testVectorAdd() {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                        1,
                        2,
                        29,    // First ID available for SPIR-V
                        0,
                        0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));         // Uses OpTypeInt to declare 64-bit integer types

        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));

        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        SPIRVId idSPIRVBuiltin = module.getNextId();

        // Add module entry function
        SPIRVId mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                mainFunctionID,
                new SPIRVLiteralString("testVectorAdd"),
                new SPIRVMultipleOperands<>(idSPIRVBuiltin)
        ));


        // OpSource
        module.add(new SPIRVOpSource(
                SPIRVSourceLanguage.OpenCL_C(),
                new SPIRVLiteralInteger(100000),
                new SPIRVOptionalOperand<>(),
                new SPIRVOptionalOperand<>()));

        // Decoration
        // Auxiliary information such as built-in variable, stream numbers, invariance, interpolation type,
        // relaxed precision, etc., added to <id>s or structure-type members through Decorations.

        // Add Decorators for the GetGlobalID intrinsics
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(SPIRVBuiltIn.GlobalInvocationId())));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString("spirv_BuiltInGlobalInvocationId"), SPIRVLinkageType.Import())));

        // Decorates for alignment of data
        SPIRVId idx = module.getNextId();
        SPIRVId a_addr = module.getNextId();
        SPIRVId b_addr = module.getNextId();
        SPIRVId c_addr = module.getNextId();
        module.add(new SPIRVOpDecorate(idx, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(4))));
        module.add(new SPIRVOpDecorate(a_addr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(b_addr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(c_addr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Type declaration
        SPIRVId ulong = module.getNextId();
        SPIRVId uint = module.getNextId();
        SPIRVId v3ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));
        // Vector of 3 longs
        module.add(new SPIRVOpTypeVector(v3ulong, ulong, new SPIRVLiteralInteger(3)));

        // Type pointers
        // OpPointerType: Declare a new pointer type.
        // Storage Class is the Storage Class of the memory holding the object pointed to. If there was a
        // forward reference to this type from an OpTypeForwardPointer, the Storage Class of that instruction
        // must equal the Storage Class of this instruction.
        // pointer to the vector ulong
        SPIRVId ptrInputv3ulong = module.getNextId();
        // INPUT Class: Input from pipeline. Visible across all functions in the current invocation. Variables
        // declared with this storage class are read-only, and must not have initializers.
        module.add(new SPIRVOpTypePointer(ptrInputv3ulong, SPIRVStorageClass.Input(), v3ulong));

        // Type void
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        // pointer to a crossGroup of uint
        SPIRVId ptrCrossGroupUint = module.getNextId();
        // CrossWorkGroup: Shared across all invocations within a work group. Visible across all functions.
        // The OpenGL "shared" storage qualifier. OpenCL local memory.
        module.add(new SPIRVOpTypePointer(ptrCrossGroupUint, SPIRVStorageClass.CrossWorkgroup(), uint));

        // Pointer to a function
        // %9 = OpTypeFunction %void %_ptr_CrossWorkgroup_uint %_ptr_CrossWorkgroup_uint
        SPIRVId mainFunctionPre = module.getNextId();

        // Declaration of the function with all parameter types
        module.add(new SPIRVOpTypeFunction(mainFunctionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossGroupUint, ptrCrossGroupUint, ptrCrossGroupUint)));

        // ptrFunctionToPtrCrossWorkGroup ---> I dont get why we need this
        SPIRVId ptrFunctionToPtrCrossWorkGroup = module.getNextId();
        // FUNCTION: Visible only within the declaring function of the current invocation. Regular function memory.
        module.add(new SPIRVOpTypePointer(ptrFunctionToPtrCrossWorkGroup, SPIRVStorageClass.Function(), ptrCrossGroupUint));

        // Pointer to a function of type uint
        SPIRVId ptrFunctionUint = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUint, SPIRVStorageClass.Function(), uint));

        // Variable declaration
        module.add(new SPIRVOpVariable(ptrInputv3ulong, idSPIRVBuiltin, SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));

        // Function Declaration
        functionScope = module.add(new SPIRVOpFunction(voidType, mainFunctionID, SPIRVFunctionControl.DontInline(), mainFunctionPre));

        SPIRVId a = module.getNextId();
        SPIRVId b = module.getNextId();
        SPIRVId c = module.getNextId();

        // Parameter Declaration
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossGroupUint, a));   // Buffer A
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossGroupUint, b));   // Buffer B
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossGroupUint, c));   // Buffer B

        SPIRVOpLabel entry = new SPIRVOpLabel(module.getNextId());
        blockScope = functionScope.add(entry);

        blockScope.add(new SPIRVOpVariable(ptrFunctionToPtrCrossWorkGroup, a_addr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionToPtrCrossWorkGroup, b_addr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionToPtrCrossWorkGroup, c_addr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUint, idx, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(
                a_addr,
                a,
                new SPIRVOptionalOperand<>(
                        SPIRVMemoryAccess.Aligned(
                                new SPIRVLiteralInteger(8)))
        ));

        blockScope.add(new SPIRVOpStore(
                b_addr,
                b,
                new SPIRVOptionalOperand<>(
                        SPIRVMemoryAccess.Aligned(
                                new SPIRVLiteralInteger(8)))
        ));

        blockScope.add(new SPIRVOpStore(
                c_addr,
                c,
                new SPIRVOptionalOperand<>(
                        SPIRVMemoryAccess.Aligned(
                                new SPIRVLiteralInteger(8)))
        ));

        // Call Thread-ID getGlobalId(0)
        SPIRVId id19 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                v3ulong,
                id19,
                idSPIRVBuiltin,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(32)))
        ));

        // Intrinsic call
        SPIRVId call = module.getNextId();
        blockScope.add(new SPIRVOpCompositeExtract(ulong, call, id19, new SPIRVMultipleOperands<>(new SPIRVLiteralInteger(0))));

        SPIRVId conv = module.getNextId();
        // SPIRVOpUConvert: Convert unsigned width. This is either a truncate or a zero extend.
        blockScope.add(new SPIRVOpUConvert(uint, conv, call));

        // Store the globalIdx into idx variable
        blockScope.add(new SPIRVOpStore(idx, conv,new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        // Load A[i] and b[i] using the thread index stored in idx
        SPIRVId id22 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossGroupUint,
                id22,
                b_addr,    // Load B[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id23 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id23,
                idx,    // Load B[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));
        SPIRVId idxprom = module.getNextId();
        // SPIRVOpSConvert: Convert signed width. This is either a truncate or a sign extend.
        blockScope.add(new SPIRVOpSConvert(ulong, idxprom, id23));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossGroupUint,
                ptridx,
                id22,
                idxprom,
                new SPIRVMultipleOperands()
        ));

        SPIRVId id28 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id28,
                ptridx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId id29 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossGroupUint,
                id29,
                c_addr,    // Load C[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id30 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id30,
                idx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        SPIRVId idxprom1 = module.getNextId();
        blockScope.add(new SPIRVOpSConvert(ulong, idxprom1, id30));

        SPIRVId ptridx2 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossGroupUint,
                ptridx2,
                id29,
                idxprom1,
                new SPIRVMultipleOperands()
        ));

        ///// Perform ADD
        SPIRVId id33 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id33,
                ptridx2,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId add = module.getNextId();
        blockScope.add(new SPIRVOpIAdd(uint, add, id28, id33));

        SPIRVId id35 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossGroupUint,
                id35,
                a_addr,    // Load A[i]
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id36 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                uint,
                id36,
                idx,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId idxprom3 = module.getNextId();
        blockScope.add(new SPIRVOpSConvert(ulong, idxprom3, id36));

        SPIRVId ptridx4 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossGroupUint,
                ptridx4,
                id35,
                idxprom3,
                new SPIRVMultipleOperands()
        ));

        blockScope.add(new SPIRVOpStore(
                ptridx4,
                add,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))
        ));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV3.spv");
    }


    public static void testAssignWithLookUpBuffer() {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                        1,
                        2,
                        29,    // First ID available for SPIR-V
                        0,
                        0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));         // Uses OpTypeInt to declare 64-bit integer types
        module.add(new SPIRVOpCapability(SPIRVCapability.Int8()));          //

        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));

        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        SPIRVId idSPIRVBuiltin = module.getNextId();

        // Add module entry function
        SPIRVId mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                mainFunctionID,
                new SPIRVLiteralString("copyTestZero"),
                new SPIRVMultipleOperands<>()
        ));

        // OpSource
        module.add(new SPIRVOpSource(
                SPIRVSourceLanguage.OpenCL_C(),
                new SPIRVLiteralInteger(100000),
                new SPIRVOptionalOperand<>(),
                new SPIRVOptionalOperand<>()));

        // Decoration
        // Auxiliary information such as built-in variable, stream numbers, invariance, interpolation type,
        // relaxed precision, etc., added to <id>s or structure-type members through Decorations.

        // Add Decorators for the GetGlobalID intrinsics
//        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(SPIRVBuiltIn.GlobalInvocationId())));
//        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
//        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString("spirv_BuiltInGlobalInvocationId"), SPIRVLinkageType.Import())));

        SPIRVId heapBaseAddr = module.getNextId();
        SPIRVId frameBaseAddr = module.getNextId();
        SPIRVId ul0 = module.getNextId();
        SPIRVId ul1 = module.getNextId();
        SPIRVId frame = module.getNextId();

        // OpNames are not mandatory, but they help to debug
        // We can also add all variables within the kernel in this section
        module.add(new SPIRVOpName(heapBaseAddr, new SPIRVLiteralString("heapBaseAddr")));
        module.add(new SPIRVOpName(frameBaseAddr, new SPIRVLiteralString("frameBaseAddr")));
        module.add(new SPIRVOpName(ul0, new SPIRVLiteralString("ul0")));
        module.add(new SPIRVOpName(ul1, new SPIRVLiteralString("ul1")));
        module.add(new SPIRVOpName(frame, new SPIRVLiteralString("frame")));

        module.add(new SPIRVOpDecorate(heapBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frameBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul0, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul1, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frame, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Type declaration
        SPIRVId uchar = module.getNextId();
        SPIRVId ulong = module.getNextId();
        SPIRVId uint = module.getNextId();
        SPIRVId v3ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(uchar, new SPIRVLiteralInteger(8), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // CONSTANTS
        // Index for the stack-frame
        SPIRVId ulongConstant3 = module.getNextId();
        SPIRVId ulongConstant24 = module.getNextId();
        SPIRVId uintConstant50 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant3, new SPIRVContextDependentLong(BigInteger.valueOf(3))));
        module.add(new SPIRVOpConstant(ulong, ulongConstant24, new SPIRVContextDependentLong(BigInteger.valueOf(24))));
        module.add(new SPIRVOpConstant(uint, uintConstant50, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        // OpVoid
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        // Type pointers
        SPIRVId ptrCrossWorkGroupUChar  = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUChar, SPIRVStorageClass.CrossWorkgroup(), uchar));

        SPIRVId functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(
                functionPre,
                voidType,
                new SPIRVMultipleOperands<>(ptrCrossWorkGroupUChar, ulong)
                ));

        SPIRVId ptrFunctionPTRCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupUChar, SPIRVStorageClass.Function(), ptrCrossWorkGroupUChar));

        SPIRVId ptrFunctionUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrCrossWorkGroupUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrFunctionPTRCrossWorkGroupULong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupULong, SPIRVStorageClass.Function(), ptrCrossWorkGroupUlong));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        functionScope = module.add(new SPIRVOpFunction(
                voidType,
                mainFunctionID,
                SPIRVFunctionControl.DontInline(),
                functionPre
        ));

        SPIRVId heap_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossWorkGroupUChar, heap_base));

        SPIRVId frame_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ulong, frame_base));

        SPIRVId entry = module.getNextId();
        blockScope = functionScope.add(new SPIRVOpLabel(entry));

        // Variable declaration within the module
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupUChar, heapBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, frameBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul0, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul1, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupULong, frame, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(
                heapBaseAddr,
                heap_base,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))
                )));

        blockScope.add(new SPIRVOpStore(
                frameBaseAddr,
                frame_base,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))
                )));

        SPIRVId id20 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossWorkGroupUChar,
                id20,
                heapBaseAddr,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id21 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ulong,
                id21,
                frameBaseAddr,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUChar,
                ptridx,
                id20,
                id21, new SPIRVMultipleOperands<>()
        ));

        SPIRVId id23 = module.getNextId();
        blockScope.add(new SPIRVOpBitcast(
                ptrCrossWorkGroupUlong,
                id23,
                ptridx
                ));

        blockScope.add(new SPIRVOpStore(
                frame,
                id23,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))
                )));

        SPIRVId id24 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ptrCrossWorkGroupUlong,
                id24,
                frame,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId ptridx1 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUlong,
                ptridx1,
                id24,
                ulongConstant3, new SPIRVMultipleOperands<>()
        ));

        SPIRVId id27 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ulong,
                id27,
                ptridx1,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        blockScope.add(new SPIRVOpStore(
                ul0,
                id27,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))
                )));

        SPIRVId id28 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ulong,
                id28,
                ul0,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId add = module.getNextId();
        blockScope.add(new SPIRVOpIAdd(
                ulong,
                add,
                id28,
                ulongConstant24
        ));

        blockScope.add(new SPIRVOpStore(
                ul1,
                add,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))
                )));

        SPIRVId id31 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(
                ulong,
                id31,
                ul1,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))
        ));

        SPIRVId id34 = module.getNextId();
        blockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, id34, id31));

        blockScope.add(new SPIRVOpStore(
                id34,
                uintConstant50,
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4))
                )));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV4.spv");
    }


    /**
     * <code>
     *     __kernel void copyTestZero(__global int* arrayI)
     * {
     *
     * 	if (arrayI[0] == 0) {
     * 		arrayI[0] = 40;
     *        } else {
     * 		arrayI[0] = 40;
     *    }
     * }
     *
     * </code>
     */
    public static void testIfCondition() {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                        1,
                        2,
                        29,
                        0,
                        0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));

        // Extension for Import "OpenCL.std"
        SPIRVId idExtension = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idExtension, new SPIRVLiteralString("OpenCL.std")));

        // OpenCL Version Set
        module.add(new SPIRVOpSource(SPIRVSourceLanguage.OpenCL_C(), new SPIRVLiteralInteger(100000), new SPIRVOptionalOperand<>(), new SPIRVOptionalOperand<>()));

        // Indicates a 64-bit module, where the address width is equal to 64 bits.
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));


        SPIRVId arrayI = module.getNextId();
        module.add(new SPIRVOpName(arrayI, new SPIRVLiteralString("arrayI")));

        SPIRVId entry = module.getNextId();
        module.add(new SPIRVOpName(entry, new SPIRVLiteralString("entry")));

        SPIRVId ifThen = module.getNextId();
        module.add(new SPIRVOpName(ifThen, new SPIRVLiteralString("ifThen")));

        SPIRVId ifElse = module.getNextId();
        module.add(new SPIRVOpName(ifElse, new SPIRVLiteralString("ifElse")));

        SPIRVId ifEnd = module.getNextId();
        module.add(new SPIRVOpName(ifEnd, new SPIRVLiteralString("ifEnd")));

        SPIRVId aAddrName = module.getNextId();
        module.add(new SPIRVOpName(aAddrName, new SPIRVLiteralString("a.addr")));

        // Decorates
        module.add(new SPIRVOpDecorate(aAddrName, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Int 32
        SPIRVId uint = module.getNextId();
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // Int 64
        SPIRVId ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));

        // OpConstants
        // Type, result, value
        SPIRVId constant0 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, constant0, new SPIRVContextDependentLong(BigInteger.valueOf(0))));

        SPIRVId constant0Int = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant0Int, new SPIRVContextDependentInt(BigInteger.valueOf(0))));

        SPIRVId constant40Int = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant40Int, new SPIRVContextDependentInt(BigInteger.valueOf(40))));

        SPIRVId constant50t = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant50t, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        SPIRVId boolType = module.getNextId();
        module.add(new SPIRVOpTypeBool(boolType));

        // Type Void
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        // Function declaration
        SPIRVId mainFunctionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(mainFunctionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossWorkGroupUInt)));

        SPIRVId ptrFunctionPtrCrossWorkGroup = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPtrCrossWorkGroup, SPIRVStorageClass.Function(), uint));

        SPIRVId functionTypePtr = module.getNextId();
        module.add(new SPIRVOpTypePointer(functionTypePtr, SPIRVStorageClass.Function(), ptrCrossWorkGroupUInt));

        SPIRVId functionDef = module.getNextId();
        functionScope = module.add(new SPIRVOpFunction(voidType, functionDef, SPIRVFunctionControl.DontInline(), mainFunctionPre));
        functionScope.add(new SPIRVOpFunctionParameter(functionTypePtr, arrayI));

        // Entry point is define at the module level, not at the function level
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                functionDef,
                new SPIRVLiteralString("ifCheck"),
                new SPIRVMultipleOperands<>()
        ));

        SPIRVOpLabel entryLabel = new SPIRVOpLabel(entry);
        blockScope = functionScope.add(entryLabel);
        blockScope.add(new SPIRVOpVariable(functionTypePtr, aAddrName, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(aAddrName, arrayI, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId load14 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ptrCrossWorkGroupUInt, load14, aAddrName,  new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUInt,
                ptridx,
                load14,
                constant0, new SPIRVMultipleOperands<>()
        ));

        SPIRVId load18 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(uint, load18, ptridx,  new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId cmp = module.getNextId();
        blockScope.add(new SPIRVOpIEqual(boolType, cmp, load18, constant0));

        SPIRVInstScope branchConditional = blockScope.add(new SPIRVOpBranchConditional(cmp, ifThen, ifElse, new SPIRVMultipleOperands<>()));

        /////////////////////////////////
        // IF THEN SCOPE
        /////////////////////////////////
        SPIRVInstScope branchScope = branchConditional.add(new SPIRVOpLabel(ifThen));
        SPIRVId load22 = module.getNextId();
        branchScope.add(new SPIRVOpLoad(functionTypePtr, load22, aAddrName, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
        SPIRVId ptridx1 = module.getNextId();
        branchScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUInt,
                ptridx1,
                load22,
                constant0, new SPIRVMultipleOperands<>()
        ));
        branchScope.add(new SPIRVOpStore(ptridx1, constant40Int, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));
        SPIRVInstScope branch2 = branchScope.add(new SPIRVOpBranch(ifEnd));

        /////////////////////////////////
        // IF ELSE
        SPIRVInstScope ifElseScope = branchConditional.add(new SPIRVOpLabel(ifElse));
        SPIRVId load24 = module.getNextId();
        ifElseScope.add(new SPIRVOpLoad(functionTypePtr, load24, aAddrName, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
        SPIRVId ptridx2 = module.getNextId();
        ifElseScope.add(new SPIRVOpInBoundsPtrAccessChain(
                ptrCrossWorkGroupUInt,
                ptridx2,
                load24,
                constant50t, new SPIRVMultipleOperands<>()
        ));
        ifElseScope.add(new SPIRVOpStore(ptridx1, constant40Int, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));
        ifElseScope.add(new SPIRVOpBranch(ifEnd));

        /////////////////////////////////
        // IF END
        SPIRVInstScope ifEndScope = branchConditional.add(new SPIRVOpLabel(ifEnd));
        ifEndScope.add(new SPIRVOpReturn());

        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV5.spv");
    }

    /**
     * <code>
     * __kernel void copyTestZero(__global int* arrayI)
     * {
     *
     * 	for (int i = 0; i < 10; i++) {
     * 		arrayI[i] = 40;
     *        }
     * }
     * </code>
     */
    public static void testCheckFor() {

        // SPIRV Header
        SPIRVModule module = new SPIRVModule(
                new SPIRVHeader(
                        1,
                        2,
                        29,
                        0,
                        0));
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));     // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));       // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));        // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64()));

        // Extension for Import "OpenCL.std"
        SPIRVId idExtension = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idExtension, new SPIRVLiteralString("OpenCL.std")));

        // OpenCL Version Set
        module.add(new SPIRVOpSource(SPIRVSourceLanguage.OpenCL_C(), new SPIRVLiteralInteger(100000), new SPIRVOptionalOperand<>(), new SPIRVOptionalOperand<>()));

        // Indicates a 64-bit module, where the address width is equal to 64 bits.
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));


        SPIRVId arrayI = module.getNextId();
        module.add(new SPIRVOpName(arrayI, new SPIRVLiteralString("arrayI")));

        SPIRVId entry = module.getNextId();
        module.add(new SPIRVOpName(entry, new SPIRVLiteralString("entry")));

        SPIRVId forCond = module.getNextId();
        module.add(new SPIRVOpName(forCond, new SPIRVLiteralString("for.Cond")));

        SPIRVId forBody = module.getNextId();
        module.add(new SPIRVOpName(forBody, new SPIRVLiteralString("for.Body")));

        SPIRVId forEnd = module.getNextId();
        module.add(new SPIRVOpName(forEnd, new SPIRVLiteralString("forEnd")));

        SPIRVId aAddrName = module.getNextId();
        module.add(new SPIRVOpName(aAddrName, new SPIRVLiteralString("a.addr")));

        // Decorates
        module.add(new SPIRVOpDecorate(aAddrName, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Int 32
        SPIRVId uint = module.getNextId();
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // Int 64
        SPIRVId ulong = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));

        // OpConstants
        // Type, result, value
        SPIRVId constant0 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, constant0, new SPIRVContextDependentLong(BigInteger.valueOf(0))));

        SPIRVId constant0Int = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant0Int, new SPIRVContextDependentInt(BigInteger.valueOf(0))));

        SPIRVId constant40Int = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant40Int, new SPIRVContextDependentInt(BigInteger.valueOf(40))));

        SPIRVId constant50t = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant50t, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        SPIRVId constant10Int = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant10Int, new SPIRVContextDependentInt(BigInteger.valueOf(10))));

        SPIRVId constant1 = module.getNextId();
        module.add(new SPIRVOpConstant(uint, constant1, new SPIRVContextDependentInt(BigInteger.valueOf(1))));

        SPIRVId boolType = module.getNextId();
        module.add(new SPIRVOpTypeBool(boolType));

        // Type Void
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        // Function declaration
        SPIRVId mainFunctionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(mainFunctionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossWorkGroupUInt)));

        SPIRVId ptrFunctionPtrCrossWorkGroup = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPtrCrossWorkGroup, SPIRVStorageClass.Function(), uint));

        SPIRVId ptrFunctionPtrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPtrCrossWorkGroupUInt, SPIRVStorageClass.Function(), ptrCrossWorkGroupUInt));

        SPIRVId ptrFunctionUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUInt, SPIRVStorageClass.Function(), uint));

        SPIRVId functionDef = module.getNextId();
        functionScope = module.add(new SPIRVOpFunction(voidType, functionDef, SPIRVFunctionControl.DontInline(), mainFunctionPre));
        functionScope.add(new SPIRVOpFunctionParameter(ptrFunctionPtrCrossWorkGroupUInt, arrayI));

        // Entry point is define at the module level, not at the function level
        module.add(new SPIRVOpEntryPoint(
                SPIRVExecutionModel.Kernel(),
                functionDef,
                new SPIRVLiteralString("ifCheck"),
                new SPIRVMultipleOperands<>()
        ));

        SPIRVOpLabel entryLabel = new SPIRVOpLabel(entry);
        blockScope = functionScope.add(entryLabel);
        blockScope.add(new SPIRVOpVariable(ptrFunctionPtrCrossWorkGroupUInt, aAddrName, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        SPIRVId i = module.getNextId();
        blockScope.add(new SPIRVOpVariable(ptrFunctionUInt,  i, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(aAddrName, arrayI, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        blockScope.add(new SPIRVOpStore(i, constant0, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        SPIRVInstScope branchLoop = blockScope.add(new SPIRVOpBranch(forCond));

        SPIRVInstScope forCondition = branchLoop.add(new SPIRVOpLabel(forCond));

        SPIRVId load18 = module.getNextId();
        forCondition.add(new SPIRVOpLoad(uint, load18, i,  new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));
        SPIRVId conditionLoop = module.getNextId();
        forCondition.add(new SPIRVOpSLessThan(boolType, conditionLoop, load18, constant10Int));
        SPIRVInstScope branchConditional = forCondition.add(new SPIRVOpBranchConditional(conditionLoop, forBody, forEnd, new SPIRVMultipleOperands<>()));

        SPIRVInstScope forBodyScope = branchLoop.add(new SPIRVOpLabel(forBody));
        forBodyScope.add(new SPIRVOpBranch(forCond));

        SPIRVInstScope forBodyEnd = branchLoop.add(new SPIRVOpLabel(forEnd));
        forBodyEnd.add(new SPIRVOpReturn());

        functionScope.add(new SPIRVOpFunctionEnd());

        writeModuleToFile(module,"/tmp/testSPIRV5.spv");
    }

    /**
     * How to run it?
     *
     * <code>
     *     java -cp lib/target/spirv-lib-1.0-SNAPSHOT.jar uk.ac.manchester.spirvproto.lib.tests.TestVector
     * </code>
     *
     * @param args
     * @throws InvalidSPIRVModuleException
     */
    public static void main(String[] args) throws InvalidSPIRVModuleException {
        //testVectorInit();
        //testVectorCopy();
        //testVectorAdd();
        //testAssignWithLookUpBuffer();

        //testIfCondition();
        testCheckFor();
    }

}
