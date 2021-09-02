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
public class SPIRVImageChannelDataType extends SPIRVEnum {

    protected SPIRVImageChannelDataType(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVImageChannelDataType SnormInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(0, "SnormInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SnormInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(1, "SnormInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(2, "UnormInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(3, "UnormInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormShort565() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(4, "UnormShort565", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormShort555() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(5, "UnormShort555", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt101010() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(6, "UnormInt101010", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(7, "SignedInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(8, "SignedInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt32() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(9, "SignedInt32", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(10, "UnsignedInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(11, "UnsignedInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt32() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(12, "UnsignedInt32", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType HalfFloat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(13, "HalfFloat", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType Float() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(14, "Float", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt24() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(15, "UnormInt24", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt101010_2() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(16, "UnormInt101010_2", params, SPIRVCapability.Kernel());
    }
}
