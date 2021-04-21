package uk.ac.manchester.spirvproto.lib.disassembler;

import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import javax.annotation.Generated;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOperandMapper {
    public static SPIRVId mapId(SPIRVLine operands, SPIRVInstScope scope) {
        return scope.getOrAddId(operands.next());
    }

    public static SPIRVLiteralString mapLiteralString(SPIRVLine operands, SPIRVInstScope scope) {
        StringBuilder sb = new StringBuilder();
        byte[] word;
        boolean lastWord;
        do {
            word = operands.nextInBytes();
            lastWord = word[3] == 0;
            String shard;
            if (lastWord) {
                int zeroIndex = word.length;
                for (int i = 3; i >= 0; i--) {
                    if (word[i] == 0) {
                        zeroIndex = i;
                    }
                }
                shard = new String(word, 0, zeroIndex);
            } else {
                shard = new String(word);
            }
            sb.append(shard);
        } while (!lastWord);

        return new SPIRVLiteralString(sb.toString());
    }

    public static SPIRVLiteralInteger mapLiteralInteger(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVLiteralInteger(operands.next());
    }

    public static SPIRVLiteralContextDependentNumber mapLiteralContextDependentNumber(SPIRVLine operands, SPIRVInstScope scope, SPIRVId typeId) {
        SPIRVInstruction type = scope.getInstruction(typeId);
        if (type instanceof SPIRVOpTypeInt) {
            int width = ((SPIRVOpTypeInt) type)._width.value;
            int signedness = ((SPIRVOpTypeInt) type)._signedness.value;

            int widthInWords = width / 32;
            if (widthInWords <= 0) widthInWords = 1;
            byte[][] words = new byte[widthInWords][4];
            for (int i = 0; i < widthInWords; i++) {
                byte[] word = operands.nextInBytes();
                words[i][0] = word[0];
                words[i][1] = word[1];
                words[i][2] = word[2];
                words[i][3] = word[3];
            }

            byte[] numberInBytes = new byte[widthInWords * 4];
            for (int i = widthInWords - 1; i >= 0; i--) {
                int arrayIndex = i * 4;
                int wordIndex = widthInWords - i - 1;
                numberInBytes[arrayIndex + 0] = words[wordIndex][3];
                numberInBytes[arrayIndex + 1] = words[wordIndex][2];
                numberInBytes[arrayIndex + 2] = words[wordIndex][1];
                numberInBytes[arrayIndex + 3] = words[wordIndex][0];
            }

            if (width <= 32) return new SPIRVContextDependentInt(new BigInteger(1 - signedness, numberInBytes));
            if (width == 64) return new SPIRVContextDependentLong(new BigInteger(1 - signedness, numberInBytes));

            throw new RuntimeException("OpTypeInt cannot have width of " + width);
        }

        if (type instanceof SPIRVOpTypeFloat) {
            int width = ((SPIRVOpTypeFloat) type)._width.value;

            byte[][] words = new byte[width / 32][4];
            for (int i = 0; i < width / 32; i++) {
                byte[] word = operands.nextInBytes();
                words[i][0] = word[0];
                words[i][1] = word[1];
                words[i][2] = word[2];
                words[i][3] = word[3];
            }

            byte[] numberInBytes = new byte[width / 8];
            for (int i = width / 32 - 1; i >= 0; i--) {
                int arrayIndex = ((width / 32 - 1) - i) * 4;
                numberInBytes[arrayIndex + 0] = words[i][3];
                numberInBytes[arrayIndex + 1] = words[i][2];
                numberInBytes[arrayIndex + 2] = words[i][1];
                numberInBytes[arrayIndex + 3] = words[i][0];
            }

            if (width == 32) return new SPIRVContextDependentFloat(Float.intBitsToFloat(ByteBuffer.wrap(numberInBytes).getInt()));
            if (width == 64) return new SPIRVContextDependentDouble(Double.longBitsToDouble(ByteBuffer.wrap(numberInBytes).getLong()));

            throw new RuntimeException("OpTypeInt cannot have width of " + width);
        }

        throw new RuntimeException("Unknown type for ContextDependentLiteral: " + type.getClass().getName());
    }

    public static SPIRVLiteralExtInstInteger mapLiteralExtInstInteger(SPIRVLine operands, SPIRVInstScope scope) {
        int opCode = operands.next();
        return new SPIRVLiteralExtInstInteger(opCode, SPIRVExtInstMapper.get(opCode));
    }

    public static SPIRVImageOperands mapImageOperands(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVImageOperands retVal = SPIRVImageOperands.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVImageOperands.None());
        }
        if ((value & 0x0001) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.Bias(operand1));
        }
        if ((value & 0x0002) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.Lod(operand1));
        }
        if ((value & 0x0004) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            SPIRVId operand2 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.Grad(operand1, operand2));
        }
        if ((value & 0x0008) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.ConstOffset(operand1));
        }
        if ((value & 0x0010) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.Offset(operand1));
        }
        if ((value & 0x0020) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.ConstOffsets(operand1));
        }
        if ((value & 0x0040) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.Sample(operand1));
        }
        if ((value & 0x0080) != 0) {
            SPIRVId operand1 = mapId(operands, scope);
            retVal.add(SPIRVImageOperands.MinLod(operand1));
        }
        return retVal;
    }

    public static SPIRVFPFastMathMode mapFPFastMathMode(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVFPFastMathMode retVal = SPIRVFPFastMathMode.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVFPFastMathMode.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVFPFastMathMode.NotNaN());
        }
        if ((value & 0x0002) != 0) {
            retVal.add(SPIRVFPFastMathMode.NotInf());
        }
        if ((value & 0x0004) != 0) {
            retVal.add(SPIRVFPFastMathMode.NSZ());
        }
        if ((value & 0x0008) != 0) {
            retVal.add(SPIRVFPFastMathMode.AllowRecip());
        }
        if ((value & 0x0010) != 0) {
            retVal.add(SPIRVFPFastMathMode.Fast());
        }
        return retVal;
    }

    public static SPIRVSelectionControl mapSelectionControl(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVSelectionControl retVal = SPIRVSelectionControl.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVSelectionControl.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVSelectionControl.Flatten());
        }
        if ((value & 0x0002) != 0) {
            retVal.add(SPIRVSelectionControl.DontFlatten());
        }
        return retVal;
    }

    public static SPIRVLoopControl mapLoopControl(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVLoopControl retVal = SPIRVLoopControl.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVLoopControl.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVLoopControl.Unroll());
        }
        if ((value & 0x0002) != 0) {
            retVal.add(SPIRVLoopControl.DontUnroll());
        }
        if ((value & 0x0004) != 0) {
            retVal.add(SPIRVLoopControl.DependencyInfinite());
        }
        if ((value & 0x0008) != 0) {
            SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);
            retVal.add(SPIRVLoopControl.DependencyLength(operand1));
        }
        return retVal;
    }

    public static SPIRVFunctionControl mapFunctionControl(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVFunctionControl retVal = SPIRVFunctionControl.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVFunctionControl.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVFunctionControl.Inline());
        }
        if ((value & 0x0002) != 0) {
            retVal.add(SPIRVFunctionControl.DontInline());
        }
        if ((value & 0x0004) != 0) {
            retVal.add(SPIRVFunctionControl.Pure());
        }
        if ((value & 0x0008) != 0) {
            retVal.add(SPIRVFunctionControl.Const());
        }
        return retVal;
    }

    public static SPIRVMemorySemantics mapMemorySemantics(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVMemorySemantics retVal = SPIRVMemorySemantics.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVMemorySemantics.Relaxed());
        }
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVMemorySemantics.None());
        }
        if ((value & 0x0002) != 0) {
            retVal.add(SPIRVMemorySemantics.Acquire());
        }
        if ((value & 0x0004) != 0) {
            retVal.add(SPIRVMemorySemantics.Release());
        }
        if ((value & 0x0008) != 0) {
            retVal.add(SPIRVMemorySemantics.AcquireRelease());
        }
        if ((value & 0x0010) != 0) {
            retVal.add(SPIRVMemorySemantics.SequentiallyConsistent());
        }
        if ((value & 0x0040) != 0) {
            retVal.add(SPIRVMemorySemantics.UniformMemory());
        }
        if ((value & 0x0080) != 0) {
            retVal.add(SPIRVMemorySemantics.SubgroupMemory());
        }
        if ((value & 0x0100) != 0) {
            retVal.add(SPIRVMemorySemantics.WorkgroupMemory());
        }
        if ((value & 0x0200) != 0) {
            retVal.add(SPIRVMemorySemantics.CrossWorkgroupMemory());
        }
        if ((value & 0x0400) != 0) {
            retVal.add(SPIRVMemorySemantics.AtomicCounterMemory());
        }
        if ((value & 0x0800) != 0) {
            retVal.add(SPIRVMemorySemantics.ImageMemory());
        }
        return retVal;
    }

    public static SPIRVMemoryAccess mapMemoryAccess(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVMemoryAccess retVal = SPIRVMemoryAccess.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVMemoryAccess.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVMemoryAccess.Volatile());
        }
        if ((value & 0x0002) != 0) {
            SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);
            retVal.add(SPIRVMemoryAccess.Aligned(operand1));
        }
        if ((value & 0x0004) != 0) {
            retVal.add(SPIRVMemoryAccess.Nontemporal());
        }
        return retVal;
    }

    public static SPIRVKernelProfilingInfo mapKernelProfilingInfo(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        SPIRVKernelProfilingInfo retVal = SPIRVKernelProfilingInfo.None();
        if ((value & 0x0000) != 0) {
            retVal.add(SPIRVKernelProfilingInfo.None());
        }
        if ((value & 0x0001) != 0) {
            retVal.add(SPIRVKernelProfilingInfo.CmdExecTime());
        }
        return retVal;
    }

    public static SPIRVSourceLanguage mapSourceLanguage(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVSourceLanguage.Unknown();
            }
            case 1: {
                return SPIRVSourceLanguage.ESSL();
            }
            case 2: {
                return SPIRVSourceLanguage.GLSL();
            }
            case 3: {
                return SPIRVSourceLanguage.OpenCL_C();
            }
            case 4: {
                return SPIRVSourceLanguage.OpenCL_CPP();
            }
            case 5: {
                return SPIRVSourceLanguage.HLSL();
            }
            default: throw new InvalidSPIRVEnumerantException("SourceLanguage", Integer.toString(value));
        }
    }

    public static SPIRVExecutionModel mapExecutionModel(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVExecutionModel.Vertex();
            }
            case 1: {
                return SPIRVExecutionModel.TessellationControl();
            }
            case 2: {
                return SPIRVExecutionModel.TessellationEvaluation();
            }
            case 3: {
                return SPIRVExecutionModel.Geometry();
            }
            case 4: {
                return SPIRVExecutionModel.Fragment();
            }
            case 5: {
                return SPIRVExecutionModel.GLCompute();
            }
            case 6: {
                return SPIRVExecutionModel.Kernel();
            }
            default: throw new InvalidSPIRVEnumerantException("ExecutionModel", Integer.toString(value));
        }
    }

    public static SPIRVAddressingModel mapAddressingModel(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVAddressingModel.Logical();
            }
            case 1: {
                return SPIRVAddressingModel.Physical32();
            }
            case 2: {
                return SPIRVAddressingModel.Physical64();
            }
            default: throw new InvalidSPIRVEnumerantException("AddressingModel", Integer.toString(value));
        }
    }

    public static SPIRVMemoryModel mapMemoryModel(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVMemoryModel.Simple();
            }
            case 1: {
                return SPIRVMemoryModel.GLSL450();
            }
            case 2: {
                return SPIRVMemoryModel.OpenCL();
            }
            default: throw new InvalidSPIRVEnumerantException("MemoryModel", Integer.toString(value));
        }
    }

    public static SPIRVExecutionMode mapExecutionMode(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.Invocations(operand1);
            }
            case 1: {
                return SPIRVExecutionMode.SpacingEqual();
            }
            case 2: {
                return SPIRVExecutionMode.SpacingFractionalEven();
            }
            case 3: {
                return SPIRVExecutionMode.SpacingFractionalOdd();
            }
            case 4: {
                return SPIRVExecutionMode.VertexOrderCw();
            }
            case 5: {
                return SPIRVExecutionMode.VertexOrderCcw();
            }
            case 6: {
                return SPIRVExecutionMode.PixelCenterInteger();
            }
            case 7: {
                return SPIRVExecutionMode.OriginUpperLeft();
            }
            case 8: {
                return SPIRVExecutionMode.OriginLowerLeft();
            }
            case 9: {
                return SPIRVExecutionMode.EarlyFragmentTests();
            }
            case 10: {
                return SPIRVExecutionMode.PointMode();
            }
            case 11: {
                return SPIRVExecutionMode.Xfb();
            }
            case 12: {
                return SPIRVExecutionMode.DepthReplacing();
            }
            case 14: {
                return SPIRVExecutionMode.DepthGreater();
            }
            case 15: {
                return SPIRVExecutionMode.DepthLess();
            }
            case 16: {
                return SPIRVExecutionMode.DepthUnchanged();
            }
            case 17: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);
                SPIRVLiteralInteger operand2 = mapLiteralInteger(operands, scope);
                SPIRVLiteralInteger operand3 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.LocalSize(operand1, operand2, operand3);
            }
            case 18: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);
                SPIRVLiteralInteger operand2 = mapLiteralInteger(operands, scope);
                SPIRVLiteralInteger operand3 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.LocalSizeHint(operand1, operand2, operand3);
            }
            case 19: {
                return SPIRVExecutionMode.InputPoints();
            }
            case 20: {
                return SPIRVExecutionMode.InputLines();
            }
            case 21: {
                return SPIRVExecutionMode.InputLinesAdjacency();
            }
            case 22: {
                return SPIRVExecutionMode.Triangles();
            }
            case 23: {
                return SPIRVExecutionMode.InputTrianglesAdjacency();
            }
            case 24: {
                return SPIRVExecutionMode.Quads();
            }
            case 25: {
                return SPIRVExecutionMode.Isolines();
            }
            case 26: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.OutputVertices(operand1);
            }
            case 27: {
                return SPIRVExecutionMode.OutputPoints();
            }
            case 28: {
                return SPIRVExecutionMode.OutputLineStrip();
            }
            case 29: {
                return SPIRVExecutionMode.OutputTriangleStrip();
            }
            case 30: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.VecTypeHint(operand1);
            }
            case 31: {
                return SPIRVExecutionMode.ContractionOff();
            }
            case 33: {
                return SPIRVExecutionMode.Initializer();
            }
            case 34: {
                return SPIRVExecutionMode.Finalizer();
            }
            case 35: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.SubgroupSize(operand1);
            }
            case 36: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVExecutionMode.SubgroupsPerWorkgroup(operand1);
            }
            case 37: {
                SPIRVId operand1 = mapId(operands, scope);

                return SPIRVExecutionMode.SubgroupsPerWorkgroupId(operand1);
            }
            case 38: {
                SPIRVId operand1 = mapId(operands, scope);
                SPIRVId operand2 = mapId(operands, scope);
                SPIRVId operand3 = mapId(operands, scope);

                return SPIRVExecutionMode.LocalSizeId(operand1, operand2, operand3);
            }
            case 39: {
                SPIRVId operand1 = mapId(operands, scope);

                return SPIRVExecutionMode.LocalSizeHintId(operand1);
            }
            case 4446: {
                return SPIRVExecutionMode.PostDepthCoverage();
            }
            case 5027: {
                return SPIRVExecutionMode.StencilRefReplacingEXT();
            }
            default: throw new InvalidSPIRVEnumerantException("ExecutionMode", Integer.toString(value));
        }
    }

    public static SPIRVStorageClass mapStorageClass(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVStorageClass.UniformConstant();
            }
            case 1: {
                return SPIRVStorageClass.Input();
            }
            case 2: {
                return SPIRVStorageClass.Uniform();
            }
            case 3: {
                return SPIRVStorageClass.Output();
            }
            case 4: {
                return SPIRVStorageClass.Workgroup();
            }
            case 5: {
                return SPIRVStorageClass.CrossWorkgroup();
            }
            case 6: {
                return SPIRVStorageClass.Private();
            }
            case 7: {
                return SPIRVStorageClass.Function();
            }
            case 8: {
                return SPIRVStorageClass.Generic();
            }
            case 9: {
                return SPIRVStorageClass.PushConstant();
            }
            case 10: {
                return SPIRVStorageClass.AtomicCounter();
            }
            case 11: {
                return SPIRVStorageClass.Image();
            }
            case 12: {
                return SPIRVStorageClass.StorageBuffer();
            }
            default: throw new InvalidSPIRVEnumerantException("StorageClass", Integer.toString(value));
        }
    }

    public static SPIRVDim mapDim(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVDim.oneD();
            }
            case 1: {
                return SPIRVDim.twoD();
            }
            case 2: {
                return SPIRVDim.threeD();
            }
            case 3: {
                return SPIRVDim.Cube();
            }
            case 4: {
                return SPIRVDim.Rect();
            }
            case 5: {
                return SPIRVDim.Buffer();
            }
            case 6: {
                return SPIRVDim.SubpassData();
            }
            default: throw new InvalidSPIRVEnumerantException("Dim", Integer.toString(value));
        }
    }

    public static SPIRVSamplerAddressingMode mapSamplerAddressingMode(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVSamplerAddressingMode.None();
            }
            case 1: {
                return SPIRVSamplerAddressingMode.ClampToEdge();
            }
            case 2: {
                return SPIRVSamplerAddressingMode.Clamp();
            }
            case 3: {
                return SPIRVSamplerAddressingMode.Repeat();
            }
            case 4: {
                return SPIRVSamplerAddressingMode.RepeatMirrored();
            }
            default: throw new InvalidSPIRVEnumerantException("SamplerAddressingMode", Integer.toString(value));
        }
    }

    public static SPIRVSamplerFilterMode mapSamplerFilterMode(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVSamplerFilterMode.Nearest();
            }
            case 1: {
                return SPIRVSamplerFilterMode.Linear();
            }
            default: throw new InvalidSPIRVEnumerantException("SamplerFilterMode", Integer.toString(value));
        }
    }

    public static SPIRVImageFormat mapImageFormat(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVImageFormat.Unknown();
            }
            case 1: {
                return SPIRVImageFormat.Rgba32f();
            }
            case 2: {
                return SPIRVImageFormat.Rgba16f();
            }
            case 3: {
                return SPIRVImageFormat.R32f();
            }
            case 4: {
                return SPIRVImageFormat.Rgba8();
            }
            case 5: {
                return SPIRVImageFormat.Rgba8Snorm();
            }
            case 6: {
                return SPIRVImageFormat.Rg32f();
            }
            case 7: {
                return SPIRVImageFormat.Rg16f();
            }
            case 8: {
                return SPIRVImageFormat.R11fG11fB10f();
            }
            case 9: {
                return SPIRVImageFormat.R16f();
            }
            case 10: {
                return SPIRVImageFormat.Rgba16();
            }
            case 11: {
                return SPIRVImageFormat.Rgb10A2();
            }
            case 12: {
                return SPIRVImageFormat.Rg16();
            }
            case 13: {
                return SPIRVImageFormat.Rg8();
            }
            case 14: {
                return SPIRVImageFormat.R16();
            }
            case 15: {
                return SPIRVImageFormat.R8();
            }
            case 16: {
                return SPIRVImageFormat.Rgba16Snorm();
            }
            case 17: {
                return SPIRVImageFormat.Rg16Snorm();
            }
            case 18: {
                return SPIRVImageFormat.Rg8Snorm();
            }
            case 19: {
                return SPIRVImageFormat.R16Snorm();
            }
            case 20: {
                return SPIRVImageFormat.R8Snorm();
            }
            case 21: {
                return SPIRVImageFormat.Rgba32i();
            }
            case 22: {
                return SPIRVImageFormat.Rgba16i();
            }
            case 23: {
                return SPIRVImageFormat.Rgba8i();
            }
            case 24: {
                return SPIRVImageFormat.R32i();
            }
            case 25: {
                return SPIRVImageFormat.Rg32i();
            }
            case 26: {
                return SPIRVImageFormat.Rg16i();
            }
            case 27: {
                return SPIRVImageFormat.Rg8i();
            }
            case 28: {
                return SPIRVImageFormat.R16i();
            }
            case 29: {
                return SPIRVImageFormat.R8i();
            }
            case 30: {
                return SPIRVImageFormat.Rgba32ui();
            }
            case 31: {
                return SPIRVImageFormat.Rgba16ui();
            }
            case 32: {
                return SPIRVImageFormat.Rgba8ui();
            }
            case 33: {
                return SPIRVImageFormat.R32ui();
            }
            case 34: {
                return SPIRVImageFormat.Rgb10a2ui();
            }
            case 35: {
                return SPIRVImageFormat.Rg32ui();
            }
            case 36: {
                return SPIRVImageFormat.Rg16ui();
            }
            case 37: {
                return SPIRVImageFormat.Rg8ui();
            }
            case 38: {
                return SPIRVImageFormat.R16ui();
            }
            case 39: {
                return SPIRVImageFormat.R8ui();
            }
            default: throw new InvalidSPIRVEnumerantException("ImageFormat", Integer.toString(value));
        }
    }

    public static SPIRVImageChannelOrder mapImageChannelOrder(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVImageChannelOrder.R();
            }
            case 1: {
                return SPIRVImageChannelOrder.A();
            }
            case 2: {
                return SPIRVImageChannelOrder.RG();
            }
            case 3: {
                return SPIRVImageChannelOrder.RA();
            }
            case 4: {
                return SPIRVImageChannelOrder.RGB();
            }
            case 5: {
                return SPIRVImageChannelOrder.RGBA();
            }
            case 6: {
                return SPIRVImageChannelOrder.BGRA();
            }
            case 7: {
                return SPIRVImageChannelOrder.ARGB();
            }
            case 8: {
                return SPIRVImageChannelOrder.Intensity();
            }
            case 9: {
                return SPIRVImageChannelOrder.Luminance();
            }
            case 10: {
                return SPIRVImageChannelOrder.Rx();
            }
            case 11: {
                return SPIRVImageChannelOrder.RGx();
            }
            case 12: {
                return SPIRVImageChannelOrder.RGBx();
            }
            case 13: {
                return SPIRVImageChannelOrder.Depth();
            }
            case 14: {
                return SPIRVImageChannelOrder.DepthStencil();
            }
            case 15: {
                return SPIRVImageChannelOrder.sRGB();
            }
            case 16: {
                return SPIRVImageChannelOrder.sRGBx();
            }
            case 17: {
                return SPIRVImageChannelOrder.sRGBA();
            }
            case 18: {
                return SPIRVImageChannelOrder.sBGRA();
            }
            case 19: {
                return SPIRVImageChannelOrder.ABGR();
            }
            default: throw new InvalidSPIRVEnumerantException("ImageChannelOrder", Integer.toString(value));
        }
    }

    public static SPIRVImageChannelDataType mapImageChannelDataType(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVImageChannelDataType.SnormInt8();
            }
            case 1: {
                return SPIRVImageChannelDataType.SnormInt16();
            }
            case 2: {
                return SPIRVImageChannelDataType.UnormInt8();
            }
            case 3: {
                return SPIRVImageChannelDataType.UnormInt16();
            }
            case 4: {
                return SPIRVImageChannelDataType.UnormShort565();
            }
            case 5: {
                return SPIRVImageChannelDataType.UnormShort555();
            }
            case 6: {
                return SPIRVImageChannelDataType.UnormInt101010();
            }
            case 7: {
                return SPIRVImageChannelDataType.SignedInt8();
            }
            case 8: {
                return SPIRVImageChannelDataType.SignedInt16();
            }
            case 9: {
                return SPIRVImageChannelDataType.SignedInt32();
            }
            case 10: {
                return SPIRVImageChannelDataType.UnsignedInt8();
            }
            case 11: {
                return SPIRVImageChannelDataType.UnsignedInt16();
            }
            case 12: {
                return SPIRVImageChannelDataType.UnsignedInt32();
            }
            case 13: {
                return SPIRVImageChannelDataType.HalfFloat();
            }
            case 14: {
                return SPIRVImageChannelDataType.Float();
            }
            case 15: {
                return SPIRVImageChannelDataType.UnormInt24();
            }
            case 16: {
                return SPIRVImageChannelDataType.UnormInt101010_2();
            }
            default: throw new InvalidSPIRVEnumerantException("ImageChannelDataType", Integer.toString(value));
        }
    }

    public static SPIRVFPRoundingMode mapFPRoundingMode(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVFPRoundingMode.RTE();
            }
            case 1: {
                return SPIRVFPRoundingMode.RTZ();
            }
            case 2: {
                return SPIRVFPRoundingMode.RTP();
            }
            case 3: {
                return SPIRVFPRoundingMode.RTN();
            }
            default: throw new InvalidSPIRVEnumerantException("FPRoundingMode", Integer.toString(value));
        }
    }

    public static SPIRVLinkageType mapLinkageType(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVLinkageType.Export();
            }
            case 1: {
                return SPIRVLinkageType.Import();
            }
            default: throw new InvalidSPIRVEnumerantException("LinkageType", Integer.toString(value));
        }
    }

    public static SPIRVAccessQualifier mapAccessQualifier(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVAccessQualifier.ReadOnly();
            }
            case 1: {
                return SPIRVAccessQualifier.WriteOnly();
            }
            case 2: {
                return SPIRVAccessQualifier.ReadWrite();
            }
            default: throw new InvalidSPIRVEnumerantException("AccessQualifier", Integer.toString(value));
        }
    }

    public static SPIRVFunctionParameterAttribute mapFunctionParameterAttribute(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVFunctionParameterAttribute.Zext();
            }
            case 1: {
                return SPIRVFunctionParameterAttribute.Sext();
            }
            case 2: {
                return SPIRVFunctionParameterAttribute.ByVal();
            }
            case 3: {
                return SPIRVFunctionParameterAttribute.Sret();
            }
            case 4: {
                return SPIRVFunctionParameterAttribute.NoAlias();
            }
            case 5: {
                return SPIRVFunctionParameterAttribute.NoCapture();
            }
            case 6: {
                return SPIRVFunctionParameterAttribute.NoWrite();
            }
            case 7: {
                return SPIRVFunctionParameterAttribute.NoReadWrite();
            }
            default: throw new InvalidSPIRVEnumerantException("FunctionParameterAttribute", Integer.toString(value));
        }
    }

    public static SPIRVDecoration mapDecoration(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVDecoration.RelaxedPrecision();
            }
            case 1: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.SpecId(operand1);
            }
            case 2: {
                return SPIRVDecoration.Block();
            }
            case 3: {
                return SPIRVDecoration.BufferBlock();
            }
            case 4: {
                return SPIRVDecoration.RowMajor();
            }
            case 5: {
                return SPIRVDecoration.ColMajor();
            }
            case 6: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.ArrayStride(operand1);
            }
            case 7: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.MatrixStride(operand1);
            }
            case 8: {
                return SPIRVDecoration.GLSLShared();
            }
            case 9: {
                return SPIRVDecoration.GLSLPacked();
            }
            case 10: {
                return SPIRVDecoration.CPacked();
            }
            case 11: {
                SPIRVBuiltIn operand1 = mapBuiltIn(operands, scope);

                return SPIRVDecoration.BuiltIn(operand1);
            }
            case 13: {
                return SPIRVDecoration.NoPerspective();
            }
            case 14: {
                return SPIRVDecoration.Flat();
            }
            case 15: {
                return SPIRVDecoration.Patch();
            }
            case 16: {
                return SPIRVDecoration.Centroid();
            }
            case 17: {
                return SPIRVDecoration.Sample();
            }
            case 18: {
                return SPIRVDecoration.Invariant();
            }
            case 19: {
                return SPIRVDecoration.Restrict();
            }
            case 20: {
                return SPIRVDecoration.Aliased();
            }
            case 21: {
                return SPIRVDecoration.Volatile();
            }
            case 22: {
                return SPIRVDecoration.Constant();
            }
            case 23: {
                return SPIRVDecoration.Coherent();
            }
            case 24: {
                return SPIRVDecoration.NonWritable();
            }
            case 25: {
                return SPIRVDecoration.NonReadable();
            }
            case 26: {
                return SPIRVDecoration.Uniform();
            }
            case 28: {
                return SPIRVDecoration.SaturatedConversion();
            }
            case 29: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Stream(operand1);
            }
            case 30: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Location(operand1);
            }
            case 31: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Component(operand1);
            }
            case 32: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Index(operand1);
            }
            case 33: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Binding(operand1);
            }
            case 34: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.DescriptorSet(operand1);
            }
            case 35: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Offset(operand1);
            }
            case 36: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.XfbBuffer(operand1);
            }
            case 37: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.XfbStride(operand1);
            }
            case 38: {
                SPIRVFunctionParameterAttribute operand1 = mapFunctionParameterAttribute(operands, scope);

                return SPIRVDecoration.FuncParamAttr(operand1);
            }
            case 39: {
                SPIRVFPRoundingMode operand1 = mapFPRoundingMode(operands, scope);

                return SPIRVDecoration.FPRoundingMode(operand1);
            }
            case 40: {
                SPIRVFPFastMathMode operand1 = mapFPFastMathMode(operands, scope);

                return SPIRVDecoration.FPFastMathMode(operand1);
            }
            case 41: {
                SPIRVLiteralString operand1 = mapLiteralString(operands, scope);
                SPIRVLinkageType operand2 = mapLinkageType(operands, scope);

                return SPIRVDecoration.LinkageAttributes(operand1, operand2);
            }
            case 42: {
                return SPIRVDecoration.NoContraction();
            }
            case 43: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.InputAttachmentIndex(operand1);
            }
            case 44: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.Alignment(operand1);
            }
            case 45: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.MaxByteOffset(operand1);
            }
            case 46: {
                SPIRVId operand1 = mapId(operands, scope);

                return SPIRVDecoration.AlignmentId(operand1);
            }
            case 47: {
                SPIRVId operand1 = mapId(operands, scope);

                return SPIRVDecoration.MaxByteOffsetId(operand1);
            }
            case 4999: {
                return SPIRVDecoration.ExplicitInterpAMD();
            }
            case 5248: {
                return SPIRVDecoration.OverrideCoverageNV();
            }
            case 5250: {
                return SPIRVDecoration.PassthroughNV();
            }
            case 5252: {
                return SPIRVDecoration.ViewportRelativeNV();
            }
            case 5256: {
                SPIRVLiteralInteger operand1 = mapLiteralInteger(operands, scope);

                return SPIRVDecoration.SecondaryViewportRelativeNV(operand1);
            }
            case 5634: {
                SPIRVId operand1 = mapId(operands, scope);

                return SPIRVDecoration.HlslCounterBufferGOOGLE(operand1);
            }
            case 5635: {
                SPIRVLiteralString operand1 = mapLiteralString(operands, scope);

                return SPIRVDecoration.HlslSemanticGOOGLE(operand1);
            }
            default: throw new InvalidSPIRVEnumerantException("Decoration", Integer.toString(value));
        }
    }

    public static SPIRVBuiltIn mapBuiltIn(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVBuiltIn.Position();
            }
            case 1: {
                return SPIRVBuiltIn.PointSize();
            }
            case 3: {
                return SPIRVBuiltIn.ClipDistance();
            }
            case 4: {
                return SPIRVBuiltIn.CullDistance();
            }
            case 5: {
                return SPIRVBuiltIn.VertexId();
            }
            case 6: {
                return SPIRVBuiltIn.InstanceId();
            }
            case 7: {
                return SPIRVBuiltIn.PrimitiveId();
            }
            case 8: {
                return SPIRVBuiltIn.InvocationId();
            }
            case 9: {
                return SPIRVBuiltIn.Layer();
            }
            case 10: {
                return SPIRVBuiltIn.ViewportIndex();
            }
            case 11: {
                return SPIRVBuiltIn.TessLevelOuter();
            }
            case 12: {
                return SPIRVBuiltIn.TessLevelInner();
            }
            case 13: {
                return SPIRVBuiltIn.TessCoord();
            }
            case 14: {
                return SPIRVBuiltIn.PatchVertices();
            }
            case 15: {
                return SPIRVBuiltIn.FragCoord();
            }
            case 16: {
                return SPIRVBuiltIn.PointCoord();
            }
            case 17: {
                return SPIRVBuiltIn.FrontFacing();
            }
            case 18: {
                return SPIRVBuiltIn.SampleId();
            }
            case 19: {
                return SPIRVBuiltIn.SamplePosition();
            }
            case 20: {
                return SPIRVBuiltIn.SampleMask();
            }
            case 22: {
                return SPIRVBuiltIn.FragDepth();
            }
            case 23: {
                return SPIRVBuiltIn.HelperInvocation();
            }
            case 24: {
                return SPIRVBuiltIn.NumWorkgroups();
            }
            case 25: {
                return SPIRVBuiltIn.WorkgroupSize();
            }
            case 26: {
                return SPIRVBuiltIn.WorkgroupId();
            }
            case 27: {
                return SPIRVBuiltIn.LocalInvocationId();
            }
            case 28: {
                return SPIRVBuiltIn.GlobalInvocationId();
            }
            case 29: {
                return SPIRVBuiltIn.LocalInvocationIndex();
            }
            case 30: {
                return SPIRVBuiltIn.WorkDim();
            }
            case 31: {
                return SPIRVBuiltIn.GlobalSize();
            }
            case 32: {
                return SPIRVBuiltIn.EnqueuedWorkgroupSize();
            }
            case 33: {
                return SPIRVBuiltIn.GlobalOffset();
            }
            case 34: {
                return SPIRVBuiltIn.GlobalLinearId();
            }
            case 36: {
                return SPIRVBuiltIn.SubgroupSize();
            }
            case 37: {
                return SPIRVBuiltIn.SubgroupMaxSize();
            }
            case 38: {
                return SPIRVBuiltIn.NumSubgroups();
            }
            case 39: {
                return SPIRVBuiltIn.NumEnqueuedSubgroups();
            }
            case 40: {
                return SPIRVBuiltIn.SubgroupId();
            }
            case 41: {
                return SPIRVBuiltIn.SubgroupLocalInvocationId();
            }
            case 42: {
                return SPIRVBuiltIn.VertexIndex();
            }
            case 43: {
                return SPIRVBuiltIn.InstanceIndex();
            }
            case 4416: {
                return SPIRVBuiltIn.SubgroupEqMaskKHR();
            }
            case 4417: {
                return SPIRVBuiltIn.SubgroupGeMaskKHR();
            }
            case 4418: {
                return SPIRVBuiltIn.SubgroupGtMaskKHR();
            }
            case 4419: {
                return SPIRVBuiltIn.SubgroupLeMaskKHR();
            }
            case 4420: {
                return SPIRVBuiltIn.SubgroupLtMaskKHR();
            }
            case 4424: {
                return SPIRVBuiltIn.BaseVertex();
            }
            case 4425: {
                return SPIRVBuiltIn.BaseInstance();
            }
            case 4426: {
                return SPIRVBuiltIn.DrawIndex();
            }
            case 4438: {
                return SPIRVBuiltIn.DeviceIndex();
            }
            case 4440: {
                return SPIRVBuiltIn.ViewIndex();
            }
            case 4992: {
                return SPIRVBuiltIn.BaryCoordNoPerspAMD();
            }
            case 4993: {
                return SPIRVBuiltIn.BaryCoordNoPerspCentroidAMD();
            }
            case 4994: {
                return SPIRVBuiltIn.BaryCoordNoPerspSampleAMD();
            }
            case 4995: {
                return SPIRVBuiltIn.BaryCoordSmoothAMD();
            }
            case 4996: {
                return SPIRVBuiltIn.BaryCoordSmoothCentroidAMD();
            }
            case 4997: {
                return SPIRVBuiltIn.BaryCoordSmoothSampleAMD();
            }
            case 4998: {
                return SPIRVBuiltIn.BaryCoordPullModelAMD();
            }
            case 5014: {
                return SPIRVBuiltIn.FragStencilRefEXT();
            }
            case 5253: {
                return SPIRVBuiltIn.ViewportMaskNV();
            }
            case 5257: {
                return SPIRVBuiltIn.SecondaryPositionNV();
            }
            case 5258: {
                return SPIRVBuiltIn.SecondaryViewportMaskNV();
            }
            case 5261: {
                return SPIRVBuiltIn.PositionPerViewNV();
            }
            case 5262: {
                return SPIRVBuiltIn.ViewportMaskPerViewNV();
            }
            default: throw new InvalidSPIRVEnumerantException("BuiltIn", Integer.toString(value));
        }
    }

    public static SPIRVScope mapScope(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVScope.CrossDevice();
            }
            case 1: {
                return SPIRVScope.Device();
            }
            case 2: {
                return SPIRVScope.Workgroup();
            }
            case 3: {
                return SPIRVScope.Subgroup();
            }
            case 4: {
                return SPIRVScope.Invocation();
            }
            default: throw new InvalidSPIRVEnumerantException("Scope", Integer.toString(value));
        }
    }

    public static SPIRVGroupOperation mapGroupOperation(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVGroupOperation.Reduce();
            }
            case 1: {
                return SPIRVGroupOperation.InclusiveScan();
            }
            case 2: {
                return SPIRVGroupOperation.ExclusiveScan();
            }
            default: throw new InvalidSPIRVEnumerantException("GroupOperation", Integer.toString(value));
        }
    }

    public static SPIRVKernelEnqueueFlags mapKernelEnqueueFlags(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVKernelEnqueueFlags.NoWait();
            }
            case 1: {
                return SPIRVKernelEnqueueFlags.WaitKernel();
            }
            case 2: {
                return SPIRVKernelEnqueueFlags.WaitWorkGroup();
            }
            default: throw new InvalidSPIRVEnumerantException("KernelEnqueueFlags", Integer.toString(value));
        }
    }

    public static SPIRVCapability mapCapability(SPIRVLine operands, SPIRVInstScope scope) {
        int value = operands.next();
        switch (value) {
            case 0: {
                return SPIRVCapability.Matrix();
            }
            case 1: {
                return SPIRVCapability.Shader();
            }
            case 2: {
                return SPIRVCapability.Geometry();
            }
            case 3: {
                return SPIRVCapability.Tessellation();
            }
            case 4: {
                return SPIRVCapability.Addresses();
            }
            case 5: {
                return SPIRVCapability.Linkage();
            }
            case 6: {
                return SPIRVCapability.Kernel();
            }
            case 7: {
                return SPIRVCapability.Vector16();
            }
            case 8: {
                return SPIRVCapability.Float16Buffer();
            }
            case 9: {
                return SPIRVCapability.Float16();
            }
            case 10: {
                return SPIRVCapability.Float64();
            }
            case 11: {
                return SPIRVCapability.Int64();
            }
            case 12: {
                return SPIRVCapability.Int64Atomics();
            }
            case 13: {
                return SPIRVCapability.ImageBasic();
            }
            case 14: {
                return SPIRVCapability.ImageReadWrite();
            }
            case 15: {
                return SPIRVCapability.ImageMipmap();
            }
            case 17: {
                return SPIRVCapability.Pipes();
            }
            case 18: {
                return SPIRVCapability.Groups();
            }
            case 19: {
                return SPIRVCapability.DeviceEnqueue();
            }
            case 20: {
                return SPIRVCapability.LiteralSampler();
            }
            case 21: {
                return SPIRVCapability.AtomicStorage();
            }
            case 22: {
                return SPIRVCapability.Int16();
            }
            case 23: {
                return SPIRVCapability.TessellationPointSize();
            }
            case 24: {
                return SPIRVCapability.GeometryPointSize();
            }
            case 25: {
                return SPIRVCapability.ImageGatherExtended();
            }
            case 27: {
                return SPIRVCapability.StorageImageMultisample();
            }
            case 28: {
                return SPIRVCapability.UniformBufferArrayDynamicIndexing();
            }
            case 29: {
                return SPIRVCapability.SampledImageArrayDynamicIndexing();
            }
            case 30: {
                return SPIRVCapability.StorageBufferArrayDynamicIndexing();
            }
            case 31: {
                return SPIRVCapability.StorageImageArrayDynamicIndexing();
            }
            case 32: {
                return SPIRVCapability.ClipDistance();
            }
            case 33: {
                return SPIRVCapability.CullDistance();
            }
            case 34: {
                return SPIRVCapability.ImageCubeArray();
            }
            case 35: {
                return SPIRVCapability.SampleRateShading();
            }
            case 36: {
                return SPIRVCapability.ImageRect();
            }
            case 37: {
                return SPIRVCapability.SampledRect();
            }
            case 38: {
                return SPIRVCapability.GenericPointer();
            }
            case 39: {
                return SPIRVCapability.Int8();
            }
            case 40: {
                return SPIRVCapability.InputAttachment();
            }
            case 41: {
                return SPIRVCapability.SparseResidency();
            }
            case 42: {
                return SPIRVCapability.MinLod();
            }
            case 43: {
                return SPIRVCapability.Sampled1D();
            }
            case 44: {
                return SPIRVCapability.Image1D();
            }
            case 45: {
                return SPIRVCapability.SampledCubeArray();
            }
            case 46: {
                return SPIRVCapability.SampledBuffer();
            }
            case 47: {
                return SPIRVCapability.ImageBuffer();
            }
            case 48: {
                return SPIRVCapability.ImageMSArray();
            }
            case 49: {
                return SPIRVCapability.StorageImageExtendedFormats();
            }
            case 50: {
                return SPIRVCapability.ImageQuery();
            }
            case 51: {
                return SPIRVCapability.DerivativeControl();
            }
            case 52: {
                return SPIRVCapability.InterpolationFunction();
            }
            case 53: {
                return SPIRVCapability.TransformFeedback();
            }
            case 54: {
                return SPIRVCapability.GeometryStreams();
            }
            case 55: {
                return SPIRVCapability.StorageImageReadWithoutFormat();
            }
            case 56: {
                return SPIRVCapability.StorageImageWriteWithoutFormat();
            }
            case 57: {
                return SPIRVCapability.MultiViewport();
            }
            case 58: {
                return SPIRVCapability.SubgroupDispatch();
            }
            case 59: {
                return SPIRVCapability.NamedBarrier();
            }
            case 60: {
                return SPIRVCapability.PipeStorage();
            }
            case 4423: {
                return SPIRVCapability.SubgroupBallotKHR();
            }
            case 4427: {
                return SPIRVCapability.DrawParameters();
            }
            case 4431: {
                return SPIRVCapability.SubgroupVoteKHR();
            }
            case 4433: {
                return SPIRVCapability.StorageBuffer16BitAccess();
            }
            case 4434: {
                return SPIRVCapability.StorageUniform16();
            }
            case 4435: {
                return SPIRVCapability.StoragePushConstant16();
            }
            case 4436: {
                return SPIRVCapability.StorageInputOutput16();
            }
            case 4437: {
                return SPIRVCapability.DeviceGroup();
            }
            case 4439: {
                return SPIRVCapability.MultiView();
            }
            case 4441: {
                return SPIRVCapability.VariablePointersStorageBuffer();
            }
            case 4442: {
                return SPIRVCapability.VariablePointers();
            }
            case 4445: {
                return SPIRVCapability.AtomicStorageOps();
            }
            case 4447: {
                return SPIRVCapability.SampleMaskPostDepthCoverage();
            }
            case 5009: {
                return SPIRVCapability.ImageGatherBiasLodAMD();
            }
            case 5010: {
                return SPIRVCapability.FragmentMaskAMD();
            }
            case 5013: {
                return SPIRVCapability.StencilExportEXT();
            }
            case 5015: {
                return SPIRVCapability.ImageReadWriteLodAMD();
            }
            case 5249: {
                return SPIRVCapability.SampleMaskOverrideCoverageNV();
            }
            case 5251: {
                return SPIRVCapability.GeometryShaderPassthroughNV();
            }
            case 5254: {
                return SPIRVCapability.ShaderViewportIndexLayerEXT();
            }
            case 5255: {
                return SPIRVCapability.ShaderViewportMaskNV();
            }
            case 5259: {
                return SPIRVCapability.ShaderStereoViewNV();
            }
            case 5260: {
                return SPIRVCapability.PerViewAttributesNV();
            }
            case 5568: {
                return SPIRVCapability.SubgroupShuffleINTEL();
            }
            case 5569: {
                return SPIRVCapability.SubgroupBufferBlockIOINTEL();
            }
            case 5570: {
                return SPIRVCapability.SubgroupImageBlockIOINTEL();
            }
            default: throw new InvalidSPIRVEnumerantException("Capability", Integer.toString(value));
        }
    }

    public static SPIRVLiteralSpecConstantOpInteger mapLiteralSpecConstantOpInteger(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVLiteralSpecConstantOpInteger(operands.next());
    }

    public static SPIRVPairLiteralIntegerIdRef mapPairLiteralIntegerIdRef(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVPairLiteralIntegerIdRef(mapLiteralInteger(operands, scope), mapId(operands, scope));
    }

    public static SPIRVPairIdRefLiteralInteger mapPairIdRefLiteralInteger(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVPairIdRefLiteralInteger(mapId(operands, scope), mapLiteralInteger(operands, scope));
    }

    public static SPIRVPairIdRefIdRef mapPairIdRefIdRef(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVPairIdRefIdRef(mapId(operands, scope), mapId(operands, scope));
    }

}

