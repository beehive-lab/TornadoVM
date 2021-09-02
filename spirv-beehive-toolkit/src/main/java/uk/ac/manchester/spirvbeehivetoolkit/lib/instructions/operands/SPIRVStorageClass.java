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

package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirvbeehivetoolkit.generator")
public class SPIRVStorageClass extends SPIRVEnum {

    protected SPIRVStorageClass(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVStorageClass UniformConstant() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(0, "UniformConstant", params);
    }
    public static SPIRVStorageClass Input() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(1, "Input", params);
    }
    public static SPIRVStorageClass Uniform() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(2, "Uniform", params, SPIRVCapability.Shader());
    }
    public static SPIRVStorageClass Output() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(3, "Output", params, SPIRVCapability.Shader());
    }
    public static SPIRVStorageClass Workgroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(4, "Workgroup", params);
    }
    public static SPIRVStorageClass CrossWorkgroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(5, "CrossWorkgroup", params);
    }
    public static SPIRVStorageClass Private() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(6, "Private", params, SPIRVCapability.Shader());
    }
    public static SPIRVStorageClass Function() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(7, "Function", params);
    }
    public static SPIRVStorageClass Generic() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(8, "Generic", params, SPIRVCapability.GenericPointer());
    }
    public static SPIRVStorageClass PushConstant() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(9, "PushConstant", params, SPIRVCapability.Shader());
    }
    public static SPIRVStorageClass AtomicCounter() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(10, "AtomicCounter", params, SPIRVCapability.AtomicStorage());
    }
    public static SPIRVStorageClass Image() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(11, "Image", params);
    }
    public static SPIRVStorageClass StorageBuffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVStorageClass(12, "StorageBuffer", params, SPIRVCapability.Shader());
    }
}
