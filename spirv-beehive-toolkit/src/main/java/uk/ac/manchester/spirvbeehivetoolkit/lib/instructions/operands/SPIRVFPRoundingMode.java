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
public class SPIRVFPRoundingMode extends SPIRVEnum {

    protected SPIRVFPRoundingMode(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVFPRoundingMode RTE() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(0, "RTE", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTZ() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(1, "RTZ", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTP() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(2, "RTP", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVFPRoundingMode RTN() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFPRoundingMode(3, "RTN", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
}
