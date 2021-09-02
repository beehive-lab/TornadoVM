/*
* MIT License
*
* Copyright (c) 2021, APT Group, Department of Computer Science,
* The University of Manchester.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package uk.ac.manchester.spirvbeehivetoolkit.lib.assembler;

import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.*;

import java.math.BigInteger;
import java.util.Iterator;
import javax.annotation.Generated;

@Generated("beehive-lab.spirvbeehivetoolkit.generator")
class SPIRVOperandMapper {
    public static SPIRVId mapId(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        return scope.getOrCreateId(tokens.next().value);
    }

    public static SPIRVLiteralString mapLiteralString(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        return new SPIRVLiteralString(token.value.substring(1, token.value.length() - 1));
    }

    public static SPIRVLiteralInteger mapLiteralInteger(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        return new SPIRVLiteralInteger(Integer.parseInt(tokens.next().value));
    }

    public static SPIRVLiteralContextDependentNumber mapLiteralContextDependentNumber(Iterator<SPIRVToken> tokens, SPIRVInstScope scope, SPIRVId typeId) {
        SPIRVInstruction type = scope.getInstruction(typeId);
        String number = tokens.next().value;
        if (type instanceof SPIRVOpTypeInt) {
            int width = ((SPIRVOpTypeInt) type)._width.value;

            if (width <= 32) return new SPIRVContextDependentInt(new BigInteger(number));
            if (width == 64) return new SPIRVContextDependentLong(new BigInteger(number));

            throw new RuntimeException("OpTypeInt cannot have width of " + width);
        }

        if (type instanceof SPIRVOpTypeFloat) {
            int width = ((SPIRVOpTypeFloat) type)._width.value;

            if (width == 32) return new SPIRVContextDependentFloat(Float.parseFloat(number));
            if (width == 64) return new SPIRVContextDependentDouble(Double.parseDouble(number));

            throw new RuntimeException("OpTypeFloat cannot have width of " + width);
        }

        throw new RuntimeException("Unknown type instruction for ContextDependentNumber: " + type.getClass().getName());
    }

    public static SPIRVLiteralExtInstInteger mapLiteralExtInstInteger(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String name = tokens.next().value;
        return new SPIRVLiteralExtInstInteger(SPIRVExtInstMapper.get(name), name);
    }

    public static SPIRVImageOperands mapImageOperands(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVImageOperands retVal = SPIRVImageOperands.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVImageOperands.None());
                    break;
                }
                case "Bias": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.Bias(operand1));
                    break;
                }
                case "Lod": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.Lod(operand1));
                    break;
                }
                case "Grad": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.Grad(operand1, operand2));
                    break;
                }
                case "ConstOffset": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.ConstOffset(operand1));
                    break;
                }
                case "Offset": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.Offset(operand1));
                    break;
                }
                case "ConstOffsets": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.ConstOffsets(operand1));
                    break;
                }
                case "Sample": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.Sample(operand1));
                    break;
                }
                case "MinLod": {
                    SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                    retVal.add(SPIRVImageOperands.MinLod(operand1));
                    break;
                }
                default: throw new IllegalArgumentException("ImageOperands: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVFPFastMathMode mapFPFastMathMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVFPFastMathMode retVal = SPIRVFPFastMathMode.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVFPFastMathMode.None());
                    break;
                }
                case "NotNaN": {
                    retVal.add(SPIRVFPFastMathMode.NotNaN());
                    break;
                }
                case "NotInf": {
                    retVal.add(SPIRVFPFastMathMode.NotInf());
                    break;
                }
                case "NSZ": {
                    retVal.add(SPIRVFPFastMathMode.NSZ());
                    break;
                }
                case "AllowRecip": {
                    retVal.add(SPIRVFPFastMathMode.AllowRecip());
                    break;
                }
                case "Fast": {
                    retVal.add(SPIRVFPFastMathMode.Fast());
                    break;
                }
                default: throw new IllegalArgumentException("FPFastMathMode: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVSelectionControl mapSelectionControl(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVSelectionControl retVal = SPIRVSelectionControl.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVSelectionControl.None());
                    break;
                }
                case "Flatten": {
                    retVal.add(SPIRVSelectionControl.Flatten());
                    break;
                }
                case "DontFlatten": {
                    retVal.add(SPIRVSelectionControl.DontFlatten());
                    break;
                }
                default: throw new IllegalArgumentException("SelectionControl: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVLoopControl mapLoopControl(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVLoopControl retVal = SPIRVLoopControl.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVLoopControl.None());
                    break;
                }
                case "Unroll": {
                    retVal.add(SPIRVLoopControl.Unroll());
                    break;
                }
                case "DontUnroll": {
                    retVal.add(SPIRVLoopControl.DontUnroll());
                    break;
                }
                case "DependencyInfinite": {
                    retVal.add(SPIRVLoopControl.DependencyInfinite());
                    break;
                }
                case "DependencyLength": {
                    SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                    retVal.add(SPIRVLoopControl.DependencyLength(operand1));
                    break;
                }
                default: throw new IllegalArgumentException("LoopControl: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVFunctionControl mapFunctionControl(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVFunctionControl retVal = SPIRVFunctionControl.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVFunctionControl.None());
                    break;
                }
                case "Inline": {
                    retVal.add(SPIRVFunctionControl.Inline());
                    break;
                }
                case "DontInline": {
                    retVal.add(SPIRVFunctionControl.DontInline());
                    break;
                }
                case "Pure": {
                    retVal.add(SPIRVFunctionControl.Pure());
                    break;
                }
                case "Const": {
                    retVal.add(SPIRVFunctionControl.Const());
                    break;
                }
                default: throw new IllegalArgumentException("FunctionControl: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVMemorySemantics mapMemorySemantics(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVMemorySemantics retVal = SPIRVMemorySemantics.None();
        for (String value : values) {
            switch (value) {
                case "Relaxed": {
                    retVal.add(SPIRVMemorySemantics.Relaxed());
                    break;
                }
                case "None": {
                    retVal.add(SPIRVMemorySemantics.None());
                    break;
                }
                case "Acquire": {
                    retVal.add(SPIRVMemorySemantics.Acquire());
                    break;
                }
                case "Release": {
                    retVal.add(SPIRVMemorySemantics.Release());
                    break;
                }
                case "AcquireRelease": {
                    retVal.add(SPIRVMemorySemantics.AcquireRelease());
                    break;
                }
                case "SequentiallyConsistent": {
                    retVal.add(SPIRVMemorySemantics.SequentiallyConsistent());
                    break;
                }
                case "UniformMemory": {
                    retVal.add(SPIRVMemorySemantics.UniformMemory());
                    break;
                }
                case "SubgroupMemory": {
                    retVal.add(SPIRVMemorySemantics.SubgroupMemory());
                    break;
                }
                case "WorkgroupMemory": {
                    retVal.add(SPIRVMemorySemantics.WorkgroupMemory());
                    break;
                }
                case "CrossWorkgroupMemory": {
                    retVal.add(SPIRVMemorySemantics.CrossWorkgroupMemory());
                    break;
                }
                case "AtomicCounterMemory": {
                    retVal.add(SPIRVMemorySemantics.AtomicCounterMemory());
                    break;
                }
                case "ImageMemory": {
                    retVal.add(SPIRVMemorySemantics.ImageMemory());
                    break;
                }
                default: throw new IllegalArgumentException("MemorySemantics: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVMemoryAccess mapMemoryAccess(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVMemoryAccess retVal = SPIRVMemoryAccess.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVMemoryAccess.None());
                    break;
                }
                case "Volatile": {
                    retVal.add(SPIRVMemoryAccess.Volatile());
                    break;
                }
                case "Aligned": {
                    SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                    retVal.add(SPIRVMemoryAccess.Aligned(operand1));
                    break;
                }
                case "Nontemporal": {
                    retVal.add(SPIRVMemoryAccess.Nontemporal());
                    break;
                }
                default: throw new IllegalArgumentException("MemoryAccess: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVKernelProfilingInfo mapKernelProfilingInfo(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        String[] values = tokens.next().value.split("\\|");
        SPIRVKernelProfilingInfo retVal = SPIRVKernelProfilingInfo.None();
        for (String value : values) {
            switch (value) {
                case "None": {
                    retVal.add(SPIRVKernelProfilingInfo.None());
                    break;
                }
                case "CmdExecTime": {
                    retVal.add(SPIRVKernelProfilingInfo.CmdExecTime());
                    break;
                }
                default: throw new IllegalArgumentException("KernelProfilingInfo: " + value);
            }
        }
        return retVal;
    }

    public static SPIRVSourceLanguage mapSourceLanguage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Unknown": {
                return SPIRVSourceLanguage.Unknown();
            }
            case "ESSL": {
                return SPIRVSourceLanguage.ESSL();
            }
            case "GLSL": {
                return SPIRVSourceLanguage.GLSL();
            }
            case "OpenCL_C": {
                return SPIRVSourceLanguage.OpenCL_C();
            }
            case "OpenCL_CPP": {
                return SPIRVSourceLanguage.OpenCL_CPP();
            }
            case "HLSL": {
                return SPIRVSourceLanguage.HLSL();
            }
            default: throw new IllegalArgumentException("SourceLanguage: " + token.value);
        }
    }

    public static SPIRVExecutionModel mapExecutionModel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Vertex": {
                return SPIRVExecutionModel.Vertex();
            }
            case "TessellationControl": {
                return SPIRVExecutionModel.TessellationControl();
            }
            case "TessellationEvaluation": {
                return SPIRVExecutionModel.TessellationEvaluation();
            }
            case "Geometry": {
                return SPIRVExecutionModel.Geometry();
            }
            case "Fragment": {
                return SPIRVExecutionModel.Fragment();
            }
            case "GLCompute": {
                return SPIRVExecutionModel.GLCompute();
            }
            case "Kernel": {
                return SPIRVExecutionModel.Kernel();
            }
            default: throw new IllegalArgumentException("ExecutionModel: " + token.value);
        }
    }

    public static SPIRVAddressingModel mapAddressingModel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Logical": {
                return SPIRVAddressingModel.Logical();
            }
            case "Physical32": {
                return SPIRVAddressingModel.Physical32();
            }
            case "Physical64": {
                return SPIRVAddressingModel.Physical64();
            }
            default: throw new IllegalArgumentException("AddressingModel: " + token.value);
        }
    }

    public static SPIRVMemoryModel mapMemoryModel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Simple": {
                return SPIRVMemoryModel.Simple();
            }
            case "GLSL450": {
                return SPIRVMemoryModel.GLSL450();
            }
            case "OpenCL": {
                return SPIRVMemoryModel.OpenCL();
            }
            default: throw new IllegalArgumentException("MemoryModel: " + token.value);
        }
    }

    public static SPIRVExecutionMode mapExecutionMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Invocations": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.Invocations(operand1);
            }
            case "SpacingEqual": {
                return SPIRVExecutionMode.SpacingEqual();
            }
            case "SpacingFractionalEven": {
                return SPIRVExecutionMode.SpacingFractionalEven();
            }
            case "SpacingFractionalOdd": {
                return SPIRVExecutionMode.SpacingFractionalOdd();
            }
            case "VertexOrderCw": {
                return SPIRVExecutionMode.VertexOrderCw();
            }
            case "VertexOrderCcw": {
                return SPIRVExecutionMode.VertexOrderCcw();
            }
            case "PixelCenterInteger": {
                return SPIRVExecutionMode.PixelCenterInteger();
            }
            case "OriginUpperLeft": {
                return SPIRVExecutionMode.OriginUpperLeft();
            }
            case "OriginLowerLeft": {
                return SPIRVExecutionMode.OriginLowerLeft();
            }
            case "EarlyFragmentTests": {
                return SPIRVExecutionMode.EarlyFragmentTests();
            }
            case "PointMode": {
                return SPIRVExecutionMode.PointMode();
            }
            case "Xfb": {
                return SPIRVExecutionMode.Xfb();
            }
            case "DepthReplacing": {
                return SPIRVExecutionMode.DepthReplacing();
            }
            case "DepthGreater": {
                return SPIRVExecutionMode.DepthGreater();
            }
            case "DepthLess": {
                return SPIRVExecutionMode.DepthLess();
            }
            case "DepthUnchanged": {
                return SPIRVExecutionMode.DepthUnchanged();
            }
            case "LocalSize": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.LocalSize(operand1, operand2, operand3);
            }
            case "LocalSizeHint": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.LocalSizeHint(operand1, operand2, operand3);
            }
            case "InputPoints": {
                return SPIRVExecutionMode.InputPoints();
            }
            case "InputLines": {
                return SPIRVExecutionMode.InputLines();
            }
            case "InputLinesAdjacency": {
                return SPIRVExecutionMode.InputLinesAdjacency();
            }
            case "Triangles": {
                return SPIRVExecutionMode.Triangles();
            }
            case "InputTrianglesAdjacency": {
                return SPIRVExecutionMode.InputTrianglesAdjacency();
            }
            case "Quads": {
                return SPIRVExecutionMode.Quads();
            }
            case "Isolines": {
                return SPIRVExecutionMode.Isolines();
            }
            case "OutputVertices": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.OutputVertices(operand1);
            }
            case "OutputPoints": {
                return SPIRVExecutionMode.OutputPoints();
            }
            case "OutputLineStrip": {
                return SPIRVExecutionMode.OutputLineStrip();
            }
            case "OutputTriangleStrip": {
                return SPIRVExecutionMode.OutputTriangleStrip();
            }
            case "VecTypeHint": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.VecTypeHint(operand1);
            }
            case "ContractionOff": {
                return SPIRVExecutionMode.ContractionOff();
            }
            case "Initializer": {
                return SPIRVExecutionMode.Initializer();
            }
            case "Finalizer": {
                return SPIRVExecutionMode.Finalizer();
            }
            case "SubgroupSize": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.SubgroupSize(operand1);
            }
            case "SubgroupsPerWorkgroup": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVExecutionMode.SubgroupsPerWorkgroup(operand1);
            }
            case "SubgroupsPerWorkgroupId": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVExecutionMode.SubgroupsPerWorkgroupId(operand1);
            }
            case "LocalSizeId": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
                SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVExecutionMode.LocalSizeId(operand1, operand2, operand3);
            }
            case "LocalSizeHintId": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVExecutionMode.LocalSizeHintId(operand1);
            }
            case "PostDepthCoverage": {
                return SPIRVExecutionMode.PostDepthCoverage();
            }
            case "StencilRefReplacingEXT": {
                return SPIRVExecutionMode.StencilRefReplacingEXT();
            }
            default: throw new IllegalArgumentException("ExecutionMode: " + token.value);
        }
    }

    public static SPIRVStorageClass mapStorageClass(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "UniformConstant": {
                return SPIRVStorageClass.UniformConstant();
            }
            case "Input": {
                return SPIRVStorageClass.Input();
            }
            case "Uniform": {
                return SPIRVStorageClass.Uniform();
            }
            case "Output": {
                return SPIRVStorageClass.Output();
            }
            case "Workgroup": {
                return SPIRVStorageClass.Workgroup();
            }
            case "CrossWorkgroup": {
                return SPIRVStorageClass.CrossWorkgroup();
            }
            case "Private": {
                return SPIRVStorageClass.Private();
            }
            case "Function": {
                return SPIRVStorageClass.Function();
            }
            case "Generic": {
                return SPIRVStorageClass.Generic();
            }
            case "PushConstant": {
                return SPIRVStorageClass.PushConstant();
            }
            case "AtomicCounter": {
                return SPIRVStorageClass.AtomicCounter();
            }
            case "Image": {
                return SPIRVStorageClass.Image();
            }
            case "StorageBuffer": {
                return SPIRVStorageClass.StorageBuffer();
            }
            default: throw new IllegalArgumentException("StorageClass: " + token.value);
        }
    }

    public static SPIRVDim mapDim(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "oneD": {
                return SPIRVDim.oneD();
            }
            case "twoD": {
                return SPIRVDim.twoD();
            }
            case "threeD": {
                return SPIRVDim.threeD();
            }
            case "Cube": {
                return SPIRVDim.Cube();
            }
            case "Rect": {
                return SPIRVDim.Rect();
            }
            case "Buffer": {
                return SPIRVDim.Buffer();
            }
            case "SubpassData": {
                return SPIRVDim.SubpassData();
            }
            default: throw new IllegalArgumentException("Dim: " + token.value);
        }
    }

    public static SPIRVSamplerAddressingMode mapSamplerAddressingMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "None": {
                return SPIRVSamplerAddressingMode.None();
            }
            case "ClampToEdge": {
                return SPIRVSamplerAddressingMode.ClampToEdge();
            }
            case "Clamp": {
                return SPIRVSamplerAddressingMode.Clamp();
            }
            case "Repeat": {
                return SPIRVSamplerAddressingMode.Repeat();
            }
            case "RepeatMirrored": {
                return SPIRVSamplerAddressingMode.RepeatMirrored();
            }
            default: throw new IllegalArgumentException("SamplerAddressingMode: " + token.value);
        }
    }

    public static SPIRVSamplerFilterMode mapSamplerFilterMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Nearest": {
                return SPIRVSamplerFilterMode.Nearest();
            }
            case "Linear": {
                return SPIRVSamplerFilterMode.Linear();
            }
            default: throw new IllegalArgumentException("SamplerFilterMode: " + token.value);
        }
    }

    public static SPIRVImageFormat mapImageFormat(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Unknown": {
                return SPIRVImageFormat.Unknown();
            }
            case "Rgba32f": {
                return SPIRVImageFormat.Rgba32f();
            }
            case "Rgba16f": {
                return SPIRVImageFormat.Rgba16f();
            }
            case "R32f": {
                return SPIRVImageFormat.R32f();
            }
            case "Rgba8": {
                return SPIRVImageFormat.Rgba8();
            }
            case "Rgba8Snorm": {
                return SPIRVImageFormat.Rgba8Snorm();
            }
            case "Rg32f": {
                return SPIRVImageFormat.Rg32f();
            }
            case "Rg16f": {
                return SPIRVImageFormat.Rg16f();
            }
            case "R11fG11fB10f": {
                return SPIRVImageFormat.R11fG11fB10f();
            }
            case "R16f": {
                return SPIRVImageFormat.R16f();
            }
            case "Rgba16": {
                return SPIRVImageFormat.Rgba16();
            }
            case "Rgb10A2": {
                return SPIRVImageFormat.Rgb10A2();
            }
            case "Rg16": {
                return SPIRVImageFormat.Rg16();
            }
            case "Rg8": {
                return SPIRVImageFormat.Rg8();
            }
            case "R16": {
                return SPIRVImageFormat.R16();
            }
            case "R8": {
                return SPIRVImageFormat.R8();
            }
            case "Rgba16Snorm": {
                return SPIRVImageFormat.Rgba16Snorm();
            }
            case "Rg16Snorm": {
                return SPIRVImageFormat.Rg16Snorm();
            }
            case "Rg8Snorm": {
                return SPIRVImageFormat.Rg8Snorm();
            }
            case "R16Snorm": {
                return SPIRVImageFormat.R16Snorm();
            }
            case "R8Snorm": {
                return SPIRVImageFormat.R8Snorm();
            }
            case "Rgba32i": {
                return SPIRVImageFormat.Rgba32i();
            }
            case "Rgba16i": {
                return SPIRVImageFormat.Rgba16i();
            }
            case "Rgba8i": {
                return SPIRVImageFormat.Rgba8i();
            }
            case "R32i": {
                return SPIRVImageFormat.R32i();
            }
            case "Rg32i": {
                return SPIRVImageFormat.Rg32i();
            }
            case "Rg16i": {
                return SPIRVImageFormat.Rg16i();
            }
            case "Rg8i": {
                return SPIRVImageFormat.Rg8i();
            }
            case "R16i": {
                return SPIRVImageFormat.R16i();
            }
            case "R8i": {
                return SPIRVImageFormat.R8i();
            }
            case "Rgba32ui": {
                return SPIRVImageFormat.Rgba32ui();
            }
            case "Rgba16ui": {
                return SPIRVImageFormat.Rgba16ui();
            }
            case "Rgba8ui": {
                return SPIRVImageFormat.Rgba8ui();
            }
            case "R32ui": {
                return SPIRVImageFormat.R32ui();
            }
            case "Rgb10a2ui": {
                return SPIRVImageFormat.Rgb10a2ui();
            }
            case "Rg32ui": {
                return SPIRVImageFormat.Rg32ui();
            }
            case "Rg16ui": {
                return SPIRVImageFormat.Rg16ui();
            }
            case "Rg8ui": {
                return SPIRVImageFormat.Rg8ui();
            }
            case "R16ui": {
                return SPIRVImageFormat.R16ui();
            }
            case "R8ui": {
                return SPIRVImageFormat.R8ui();
            }
            default: throw new IllegalArgumentException("ImageFormat: " + token.value);
        }
    }

    public static SPIRVImageChannelOrder mapImageChannelOrder(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "R": {
                return SPIRVImageChannelOrder.R();
            }
            case "A": {
                return SPIRVImageChannelOrder.A();
            }
            case "RG": {
                return SPIRVImageChannelOrder.RG();
            }
            case "RA": {
                return SPIRVImageChannelOrder.RA();
            }
            case "RGB": {
                return SPIRVImageChannelOrder.RGB();
            }
            case "RGBA": {
                return SPIRVImageChannelOrder.RGBA();
            }
            case "BGRA": {
                return SPIRVImageChannelOrder.BGRA();
            }
            case "ARGB": {
                return SPIRVImageChannelOrder.ARGB();
            }
            case "Intensity": {
                return SPIRVImageChannelOrder.Intensity();
            }
            case "Luminance": {
                return SPIRVImageChannelOrder.Luminance();
            }
            case "Rx": {
                return SPIRVImageChannelOrder.Rx();
            }
            case "RGx": {
                return SPIRVImageChannelOrder.RGx();
            }
            case "RGBx": {
                return SPIRVImageChannelOrder.RGBx();
            }
            case "Depth": {
                return SPIRVImageChannelOrder.Depth();
            }
            case "DepthStencil": {
                return SPIRVImageChannelOrder.DepthStencil();
            }
            case "sRGB": {
                return SPIRVImageChannelOrder.sRGB();
            }
            case "sRGBx": {
                return SPIRVImageChannelOrder.sRGBx();
            }
            case "sRGBA": {
                return SPIRVImageChannelOrder.sRGBA();
            }
            case "sBGRA": {
                return SPIRVImageChannelOrder.sBGRA();
            }
            case "ABGR": {
                return SPIRVImageChannelOrder.ABGR();
            }
            default: throw new IllegalArgumentException("ImageChannelOrder: " + token.value);
        }
    }

    public static SPIRVImageChannelDataType mapImageChannelDataType(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "SnormInt8": {
                return SPIRVImageChannelDataType.SnormInt8();
            }
            case "SnormInt16": {
                return SPIRVImageChannelDataType.SnormInt16();
            }
            case "UnormInt8": {
                return SPIRVImageChannelDataType.UnormInt8();
            }
            case "UnormInt16": {
                return SPIRVImageChannelDataType.UnormInt16();
            }
            case "UnormShort565": {
                return SPIRVImageChannelDataType.UnormShort565();
            }
            case "UnormShort555": {
                return SPIRVImageChannelDataType.UnormShort555();
            }
            case "UnormInt101010": {
                return SPIRVImageChannelDataType.UnormInt101010();
            }
            case "SignedInt8": {
                return SPIRVImageChannelDataType.SignedInt8();
            }
            case "SignedInt16": {
                return SPIRVImageChannelDataType.SignedInt16();
            }
            case "SignedInt32": {
                return SPIRVImageChannelDataType.SignedInt32();
            }
            case "UnsignedInt8": {
                return SPIRVImageChannelDataType.UnsignedInt8();
            }
            case "UnsignedInt16": {
                return SPIRVImageChannelDataType.UnsignedInt16();
            }
            case "UnsignedInt32": {
                return SPIRVImageChannelDataType.UnsignedInt32();
            }
            case "HalfFloat": {
                return SPIRVImageChannelDataType.HalfFloat();
            }
            case "Float": {
                return SPIRVImageChannelDataType.Float();
            }
            case "UnormInt24": {
                return SPIRVImageChannelDataType.UnormInt24();
            }
            case "UnormInt101010_2": {
                return SPIRVImageChannelDataType.UnormInt101010_2();
            }
            default: throw new IllegalArgumentException("ImageChannelDataType: " + token.value);
        }
    }

    public static SPIRVFPRoundingMode mapFPRoundingMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "RTE": {
                return SPIRVFPRoundingMode.RTE();
            }
            case "RTZ": {
                return SPIRVFPRoundingMode.RTZ();
            }
            case "RTP": {
                return SPIRVFPRoundingMode.RTP();
            }
            case "RTN": {
                return SPIRVFPRoundingMode.RTN();
            }
            default: throw new IllegalArgumentException("FPRoundingMode: " + token.value);
        }
    }

    public static SPIRVLinkageType mapLinkageType(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Export": {
                return SPIRVLinkageType.Export();
            }
            case "Import": {
                return SPIRVLinkageType.Import();
            }
            default: throw new IllegalArgumentException("LinkageType: " + token.value);
        }
    }

    public static SPIRVAccessQualifier mapAccessQualifier(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "ReadOnly": {
                return SPIRVAccessQualifier.ReadOnly();
            }
            case "WriteOnly": {
                return SPIRVAccessQualifier.WriteOnly();
            }
            case "ReadWrite": {
                return SPIRVAccessQualifier.ReadWrite();
            }
            default: throw new IllegalArgumentException("AccessQualifier: " + token.value);
        }
    }

    public static SPIRVFunctionParameterAttribute mapFunctionParameterAttribute(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Zext": {
                return SPIRVFunctionParameterAttribute.Zext();
            }
            case "Sext": {
                return SPIRVFunctionParameterAttribute.Sext();
            }
            case "ByVal": {
                return SPIRVFunctionParameterAttribute.ByVal();
            }
            case "Sret": {
                return SPIRVFunctionParameterAttribute.Sret();
            }
            case "NoAlias": {
                return SPIRVFunctionParameterAttribute.NoAlias();
            }
            case "NoCapture": {
                return SPIRVFunctionParameterAttribute.NoCapture();
            }
            case "NoWrite": {
                return SPIRVFunctionParameterAttribute.NoWrite();
            }
            case "NoReadWrite": {
                return SPIRVFunctionParameterAttribute.NoReadWrite();
            }
            default: throw new IllegalArgumentException("FunctionParameterAttribute: " + token.value);
        }
    }

    public static SPIRVDecoration mapDecoration(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "RelaxedPrecision": {
                return SPIRVDecoration.RelaxedPrecision();
            }
            case "SpecId": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.SpecId(operand1);
            }
            case "Block": {
                return SPIRVDecoration.Block();
            }
            case "BufferBlock": {
                return SPIRVDecoration.BufferBlock();
            }
            case "RowMajor": {
                return SPIRVDecoration.RowMajor();
            }
            case "ColMajor": {
                return SPIRVDecoration.ColMajor();
            }
            case "ArrayStride": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.ArrayStride(operand1);
            }
            case "MatrixStride": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.MatrixStride(operand1);
            }
            case "GLSLShared": {
                return SPIRVDecoration.GLSLShared();
            }
            case "GLSLPacked": {
                return SPIRVDecoration.GLSLPacked();
            }
            case "CPacked": {
                return SPIRVDecoration.CPacked();
            }
            case "BuiltIn": {
                SPIRVBuiltIn operand1 = SPIRVOperandMapper.mapBuiltIn(tokens, scope);
                return SPIRVDecoration.BuiltIn(operand1);
            }
            case "NoPerspective": {
                return SPIRVDecoration.NoPerspective();
            }
            case "Flat": {
                return SPIRVDecoration.Flat();
            }
            case "Patch": {
                return SPIRVDecoration.Patch();
            }
            case "Centroid": {
                return SPIRVDecoration.Centroid();
            }
            case "Sample": {
                return SPIRVDecoration.Sample();
            }
            case "Invariant": {
                return SPIRVDecoration.Invariant();
            }
            case "Restrict": {
                return SPIRVDecoration.Restrict();
            }
            case "Aliased": {
                return SPIRVDecoration.Aliased();
            }
            case "Volatile": {
                return SPIRVDecoration.Volatile();
            }
            case "Constant": {
                return SPIRVDecoration.Constant();
            }
            case "Coherent": {
                return SPIRVDecoration.Coherent();
            }
            case "NonWritable": {
                return SPIRVDecoration.NonWritable();
            }
            case "NonReadable": {
                return SPIRVDecoration.NonReadable();
            }
            case "Uniform": {
                return SPIRVDecoration.Uniform();
            }
            case "SaturatedConversion": {
                return SPIRVDecoration.SaturatedConversion();
            }
            case "Stream": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Stream(operand1);
            }
            case "Location": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Location(operand1);
            }
            case "Component": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Component(operand1);
            }
            case "Index": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Index(operand1);
            }
            case "Binding": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Binding(operand1);
            }
            case "DescriptorSet": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.DescriptorSet(operand1);
            }
            case "Offset": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Offset(operand1);
            }
            case "XfbBuffer": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.XfbBuffer(operand1);
            }
            case "XfbStride": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.XfbStride(operand1);
            }
            case "FuncParamAttr": {
                SPIRVFunctionParameterAttribute operand1 = SPIRVOperandMapper.mapFunctionParameterAttribute(tokens, scope);
                return SPIRVDecoration.FuncParamAttr(operand1);
            }
            case "FPRoundingMode": {
                SPIRVFPRoundingMode operand1 = SPIRVOperandMapper.mapFPRoundingMode(tokens, scope);
                return SPIRVDecoration.FPRoundingMode(operand1);
            }
            case "FPFastMathMode": {
                SPIRVFPFastMathMode operand1 = SPIRVOperandMapper.mapFPFastMathMode(tokens, scope);
                return SPIRVDecoration.FPFastMathMode(operand1);
            }
            case "LinkageAttributes": {
                SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);
                SPIRVLinkageType operand2 = SPIRVOperandMapper.mapLinkageType(tokens, scope);
                return SPIRVDecoration.LinkageAttributes(operand1, operand2);
            }
            case "NoContraction": {
                return SPIRVDecoration.NoContraction();
            }
            case "InputAttachmentIndex": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.InputAttachmentIndex(operand1);
            }
            case "Alignment": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.Alignment(operand1);
            }
            case "MaxByteOffset": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.MaxByteOffset(operand1);
            }
            case "AlignmentId": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVDecoration.AlignmentId(operand1);
            }
            case "MaxByteOffsetId": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVDecoration.MaxByteOffsetId(operand1);
            }
            case "ExplicitInterpAMD": {
                return SPIRVDecoration.ExplicitInterpAMD();
            }
            case "OverrideCoverageNV": {
                return SPIRVDecoration.OverrideCoverageNV();
            }
            case "PassthroughNV": {
                return SPIRVDecoration.PassthroughNV();
            }
            case "ViewportRelativeNV": {
                return SPIRVDecoration.ViewportRelativeNV();
            }
            case "SecondaryViewportRelativeNV": {
                SPIRVLiteralInteger operand1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
                return SPIRVDecoration.SecondaryViewportRelativeNV(operand1);
            }
            case "HlslCounterBufferGOOGLE": {
                SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
                return SPIRVDecoration.HlslCounterBufferGOOGLE(operand1);
            }
            case "HlslSemanticGOOGLE": {
                SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);
                return SPIRVDecoration.HlslSemanticGOOGLE(operand1);
            }
            default: throw new IllegalArgumentException("Decoration: " + token.value);
        }
    }

    public static SPIRVBuiltIn mapBuiltIn(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Position": {
                return SPIRVBuiltIn.Position();
            }
            case "PointSize": {
                return SPIRVBuiltIn.PointSize();
            }
            case "ClipDistance": {
                return SPIRVBuiltIn.ClipDistance();
            }
            case "CullDistance": {
                return SPIRVBuiltIn.CullDistance();
            }
            case "VertexId": {
                return SPIRVBuiltIn.VertexId();
            }
            case "InstanceId": {
                return SPIRVBuiltIn.InstanceId();
            }
            case "PrimitiveId": {
                return SPIRVBuiltIn.PrimitiveId();
            }
            case "InvocationId": {
                return SPIRVBuiltIn.InvocationId();
            }
            case "Layer": {
                return SPIRVBuiltIn.Layer();
            }
            case "ViewportIndex": {
                return SPIRVBuiltIn.ViewportIndex();
            }
            case "TessLevelOuter": {
                return SPIRVBuiltIn.TessLevelOuter();
            }
            case "TessLevelInner": {
                return SPIRVBuiltIn.TessLevelInner();
            }
            case "TessCoord": {
                return SPIRVBuiltIn.TessCoord();
            }
            case "PatchVertices": {
                return SPIRVBuiltIn.PatchVertices();
            }
            case "FragCoord": {
                return SPIRVBuiltIn.FragCoord();
            }
            case "PointCoord": {
                return SPIRVBuiltIn.PointCoord();
            }
            case "FrontFacing": {
                return SPIRVBuiltIn.FrontFacing();
            }
            case "SampleId": {
                return SPIRVBuiltIn.SampleId();
            }
            case "SamplePosition": {
                return SPIRVBuiltIn.SamplePosition();
            }
            case "SampleMask": {
                return SPIRVBuiltIn.SampleMask();
            }
            case "FragDepth": {
                return SPIRVBuiltIn.FragDepth();
            }
            case "HelperInvocation": {
                return SPIRVBuiltIn.HelperInvocation();
            }
            case "NumWorkgroups": {
                return SPIRVBuiltIn.NumWorkgroups();
            }
            case "WorkgroupSize": {
                return SPIRVBuiltIn.WorkgroupSize();
            }
            case "WorkgroupId": {
                return SPIRVBuiltIn.WorkgroupId();
            }
            case "LocalInvocationId": {
                return SPIRVBuiltIn.LocalInvocationId();
            }
            case "GlobalInvocationId": {
                return SPIRVBuiltIn.GlobalInvocationId();
            }
            case "LocalInvocationIndex": {
                return SPIRVBuiltIn.LocalInvocationIndex();
            }
            case "WorkDim": {
                return SPIRVBuiltIn.WorkDim();
            }
            case "GlobalSize": {
                return SPIRVBuiltIn.GlobalSize();
            }
            case "EnqueuedWorkgroupSize": {
                return SPIRVBuiltIn.EnqueuedWorkgroupSize();
            }
            case "GlobalOffset": {
                return SPIRVBuiltIn.GlobalOffset();
            }
            case "GlobalLinearId": {
                return SPIRVBuiltIn.GlobalLinearId();
            }
            case "SubgroupSize": {
                return SPIRVBuiltIn.SubgroupSize();
            }
            case "SubgroupMaxSize": {
                return SPIRVBuiltIn.SubgroupMaxSize();
            }
            case "NumSubgroups": {
                return SPIRVBuiltIn.NumSubgroups();
            }
            case "NumEnqueuedSubgroups": {
                return SPIRVBuiltIn.NumEnqueuedSubgroups();
            }
            case "SubgroupId": {
                return SPIRVBuiltIn.SubgroupId();
            }
            case "SubgroupLocalInvocationId": {
                return SPIRVBuiltIn.SubgroupLocalInvocationId();
            }
            case "VertexIndex": {
                return SPIRVBuiltIn.VertexIndex();
            }
            case "InstanceIndex": {
                return SPIRVBuiltIn.InstanceIndex();
            }
            case "SubgroupEqMaskKHR": {
                return SPIRVBuiltIn.SubgroupEqMaskKHR();
            }
            case "SubgroupGeMaskKHR": {
                return SPIRVBuiltIn.SubgroupGeMaskKHR();
            }
            case "SubgroupGtMaskKHR": {
                return SPIRVBuiltIn.SubgroupGtMaskKHR();
            }
            case "SubgroupLeMaskKHR": {
                return SPIRVBuiltIn.SubgroupLeMaskKHR();
            }
            case "SubgroupLtMaskKHR": {
                return SPIRVBuiltIn.SubgroupLtMaskKHR();
            }
            case "BaseVertex": {
                return SPIRVBuiltIn.BaseVertex();
            }
            case "BaseInstance": {
                return SPIRVBuiltIn.BaseInstance();
            }
            case "DrawIndex": {
                return SPIRVBuiltIn.DrawIndex();
            }
            case "DeviceIndex": {
                return SPIRVBuiltIn.DeviceIndex();
            }
            case "ViewIndex": {
                return SPIRVBuiltIn.ViewIndex();
            }
            case "BaryCoordNoPerspAMD": {
                return SPIRVBuiltIn.BaryCoordNoPerspAMD();
            }
            case "BaryCoordNoPerspCentroidAMD": {
                return SPIRVBuiltIn.BaryCoordNoPerspCentroidAMD();
            }
            case "BaryCoordNoPerspSampleAMD": {
                return SPIRVBuiltIn.BaryCoordNoPerspSampleAMD();
            }
            case "BaryCoordSmoothAMD": {
                return SPIRVBuiltIn.BaryCoordSmoothAMD();
            }
            case "BaryCoordSmoothCentroidAMD": {
                return SPIRVBuiltIn.BaryCoordSmoothCentroidAMD();
            }
            case "BaryCoordSmoothSampleAMD": {
                return SPIRVBuiltIn.BaryCoordSmoothSampleAMD();
            }
            case "BaryCoordPullModelAMD": {
                return SPIRVBuiltIn.BaryCoordPullModelAMD();
            }
            case "FragStencilRefEXT": {
                return SPIRVBuiltIn.FragStencilRefEXT();
            }
            case "ViewportMaskNV": {
                return SPIRVBuiltIn.ViewportMaskNV();
            }
            case "SecondaryPositionNV": {
                return SPIRVBuiltIn.SecondaryPositionNV();
            }
            case "SecondaryViewportMaskNV": {
                return SPIRVBuiltIn.SecondaryViewportMaskNV();
            }
            case "PositionPerViewNV": {
                return SPIRVBuiltIn.PositionPerViewNV();
            }
            case "ViewportMaskPerViewNV": {
                return SPIRVBuiltIn.ViewportMaskPerViewNV();
            }
            default: throw new IllegalArgumentException("BuiltIn: " + token.value);
        }
    }

    public static SPIRVScope mapScope(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "CrossDevice": {
                return SPIRVScope.CrossDevice();
            }
            case "Device": {
                return SPIRVScope.Device();
            }
            case "Workgroup": {
                return SPIRVScope.Workgroup();
            }
            case "Subgroup": {
                return SPIRVScope.Subgroup();
            }
            case "Invocation": {
                return SPIRVScope.Invocation();
            }
            default: throw new IllegalArgumentException("Scope: " + token.value);
        }
    }

    public static SPIRVGroupOperation mapGroupOperation(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Reduce": {
                return SPIRVGroupOperation.Reduce();
            }
            case "InclusiveScan": {
                return SPIRVGroupOperation.InclusiveScan();
            }
            case "ExclusiveScan": {
                return SPIRVGroupOperation.ExclusiveScan();
            }
            default: throw new IllegalArgumentException("GroupOperation: " + token.value);
        }
    }

    public static SPIRVKernelEnqueueFlags mapKernelEnqueueFlags(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "NoWait": {
                return SPIRVKernelEnqueueFlags.NoWait();
            }
            case "WaitKernel": {
                return SPIRVKernelEnqueueFlags.WaitKernel();
            }
            case "WaitWorkGroup": {
                return SPIRVKernelEnqueueFlags.WaitWorkGroup();
            }
            default: throw new IllegalArgumentException("KernelEnqueueFlags: " + token.value);
        }
    }

    public static SPIRVCapability mapCapability(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVToken token = tokens.next();
        switch(token.value) {
            case "Matrix": {
                return SPIRVCapability.Matrix();
            }
            case "Shader": {
                return SPIRVCapability.Shader();
            }
            case "Geometry": {
                return SPIRVCapability.Geometry();
            }
            case "Tessellation": {
                return SPIRVCapability.Tessellation();
            }
            case "Addresses": {
                return SPIRVCapability.Addresses();
            }
            case "Linkage": {
                return SPIRVCapability.Linkage();
            }
            case "Kernel": {
                return SPIRVCapability.Kernel();
            }
            case "Vector16": {
                return SPIRVCapability.Vector16();
            }
            case "Float16Buffer": {
                return SPIRVCapability.Float16Buffer();
            }
            case "Float16": {
                return SPIRVCapability.Float16();
            }
            case "Float64": {
                return SPIRVCapability.Float64();
            }
            case "Int64": {
                return SPIRVCapability.Int64();
            }
            case "Int64Atomics": {
                return SPIRVCapability.Int64Atomics();
            }
            case "ImageBasic": {
                return SPIRVCapability.ImageBasic();
            }
            case "ImageReadWrite": {
                return SPIRVCapability.ImageReadWrite();
            }
            case "ImageMipmap": {
                return SPIRVCapability.ImageMipmap();
            }
            case "Pipes": {
                return SPIRVCapability.Pipes();
            }
            case "Groups": {
                return SPIRVCapability.Groups();
            }
            case "DeviceEnqueue": {
                return SPIRVCapability.DeviceEnqueue();
            }
            case "LiteralSampler": {
                return SPIRVCapability.LiteralSampler();
            }
            case "AtomicStorage": {
                return SPIRVCapability.AtomicStorage();
            }
            case "Int16": {
                return SPIRVCapability.Int16();
            }
            case "TessellationPointSize": {
                return SPIRVCapability.TessellationPointSize();
            }
            case "GeometryPointSize": {
                return SPIRVCapability.GeometryPointSize();
            }
            case "ImageGatherExtended": {
                return SPIRVCapability.ImageGatherExtended();
            }
            case "StorageImageMultisample": {
                return SPIRVCapability.StorageImageMultisample();
            }
            case "UniformBufferArrayDynamicIndexing": {
                return SPIRVCapability.UniformBufferArrayDynamicIndexing();
            }
            case "SampledImageArrayDynamicIndexing": {
                return SPIRVCapability.SampledImageArrayDynamicIndexing();
            }
            case "StorageBufferArrayDynamicIndexing": {
                return SPIRVCapability.StorageBufferArrayDynamicIndexing();
            }
            case "StorageImageArrayDynamicIndexing": {
                return SPIRVCapability.StorageImageArrayDynamicIndexing();
            }
            case "ClipDistance": {
                return SPIRVCapability.ClipDistance();
            }
            case "CullDistance": {
                return SPIRVCapability.CullDistance();
            }
            case "ImageCubeArray": {
                return SPIRVCapability.ImageCubeArray();
            }
            case "SampleRateShading": {
                return SPIRVCapability.SampleRateShading();
            }
            case "ImageRect": {
                return SPIRVCapability.ImageRect();
            }
            case "SampledRect": {
                return SPIRVCapability.SampledRect();
            }
            case "GenericPointer": {
                return SPIRVCapability.GenericPointer();
            }
            case "Int8": {
                return SPIRVCapability.Int8();
            }
            case "InputAttachment": {
                return SPIRVCapability.InputAttachment();
            }
            case "SparseResidency": {
                return SPIRVCapability.SparseResidency();
            }
            case "MinLod": {
                return SPIRVCapability.MinLod();
            }
            case "Sampled1D": {
                return SPIRVCapability.Sampled1D();
            }
            case "Image1D": {
                return SPIRVCapability.Image1D();
            }
            case "SampledCubeArray": {
                return SPIRVCapability.SampledCubeArray();
            }
            case "SampledBuffer": {
                return SPIRVCapability.SampledBuffer();
            }
            case "ImageBuffer": {
                return SPIRVCapability.ImageBuffer();
            }
            case "ImageMSArray": {
                return SPIRVCapability.ImageMSArray();
            }
            case "StorageImageExtendedFormats": {
                return SPIRVCapability.StorageImageExtendedFormats();
            }
            case "ImageQuery": {
                return SPIRVCapability.ImageQuery();
            }
            case "DerivativeControl": {
                return SPIRVCapability.DerivativeControl();
            }
            case "InterpolationFunction": {
                return SPIRVCapability.InterpolationFunction();
            }
            case "TransformFeedback": {
                return SPIRVCapability.TransformFeedback();
            }
            case "GeometryStreams": {
                return SPIRVCapability.GeometryStreams();
            }
            case "StorageImageReadWithoutFormat": {
                return SPIRVCapability.StorageImageReadWithoutFormat();
            }
            case "StorageImageWriteWithoutFormat": {
                return SPIRVCapability.StorageImageWriteWithoutFormat();
            }
            case "MultiViewport": {
                return SPIRVCapability.MultiViewport();
            }
            case "SubgroupDispatch": {
                return SPIRVCapability.SubgroupDispatch();
            }
            case "NamedBarrier": {
                return SPIRVCapability.NamedBarrier();
            }
            case "PipeStorage": {
                return SPIRVCapability.PipeStorage();
            }
            case "SubgroupBallotKHR": {
                return SPIRVCapability.SubgroupBallotKHR();
            }
            case "DrawParameters": {
                return SPIRVCapability.DrawParameters();
            }
            case "SubgroupVoteKHR": {
                return SPIRVCapability.SubgroupVoteKHR();
            }
            case "StorageBuffer16BitAccess": {
                return SPIRVCapability.StorageBuffer16BitAccess();
            }
            case "StorageUniform16": {
                return SPIRVCapability.StorageUniform16();
            }
            case "StoragePushConstant16": {
                return SPIRVCapability.StoragePushConstant16();
            }
            case "StorageInputOutput16": {
                return SPIRVCapability.StorageInputOutput16();
            }
            case "DeviceGroup": {
                return SPIRVCapability.DeviceGroup();
            }
            case "MultiView": {
                return SPIRVCapability.MultiView();
            }
            case "VariablePointersStorageBuffer": {
                return SPIRVCapability.VariablePointersStorageBuffer();
            }
            case "VariablePointers": {
                return SPIRVCapability.VariablePointers();
            }
            case "AtomicStorageOps": {
                return SPIRVCapability.AtomicStorageOps();
            }
            case "SampleMaskPostDepthCoverage": {
                return SPIRVCapability.SampleMaskPostDepthCoverage();
            }
            case "ImageGatherBiasLodAMD": {
                return SPIRVCapability.ImageGatherBiasLodAMD();
            }
            case "FragmentMaskAMD": {
                return SPIRVCapability.FragmentMaskAMD();
            }
            case "StencilExportEXT": {
                return SPIRVCapability.StencilExportEXT();
            }
            case "ImageReadWriteLodAMD": {
                return SPIRVCapability.ImageReadWriteLodAMD();
            }
            case "SampleMaskOverrideCoverageNV": {
                return SPIRVCapability.SampleMaskOverrideCoverageNV();
            }
            case "GeometryShaderPassthroughNV": {
                return SPIRVCapability.GeometryShaderPassthroughNV();
            }
            case "ShaderViewportIndexLayerEXT": {
                return SPIRVCapability.ShaderViewportIndexLayerEXT();
            }
            case "ShaderViewportMaskNV": {
                return SPIRVCapability.ShaderViewportMaskNV();
            }
            case "ShaderStereoViewNV": {
                return SPIRVCapability.ShaderStereoViewNV();
            }
            case "PerViewAttributesNV": {
                return SPIRVCapability.PerViewAttributesNV();
            }
            case "SubgroupShuffleINTEL": {
                return SPIRVCapability.SubgroupShuffleINTEL();
            }
            case "SubgroupBufferBlockIOINTEL": {
                return SPIRVCapability.SubgroupBufferBlockIOINTEL();
            }
            case "SubgroupImageBlockIOINTEL": {
                return SPIRVCapability.SubgroupImageBlockIOINTEL();
            }
            default: throw new IllegalArgumentException("Capability: " + token.value);
        }
    }

    public static SPIRVLiteralSpecConstantOpInteger mapLiteralSpecConstantOpInteger(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        return new SPIRVLiteralSpecConstantOpInteger(Integer.decode(tokens.next().value));
    }

    public static SPIRVPairLiteralIntegerIdRef mapPairLiteralIntegerIdRef(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVLiteralInteger member1 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVId member2 = SPIRVOperandMapper.mapId(tokens, scope);
        return new SPIRVPairLiteralIntegerIdRef(member1, member2);
    }

    public static SPIRVPairIdRefLiteralInteger mapPairIdRefLiteralInteger(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId member1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger member2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        return new SPIRVPairIdRefLiteralInteger(member1, member2);
    }

    public static SPIRVPairIdRefIdRef mapPairIdRefIdRef(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId member1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId member2 = SPIRVOperandMapper.mapId(tokens, scope);
        return new SPIRVPairIdRefIdRef(member1, member2);
    }

}
