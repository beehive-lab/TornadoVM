/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.ocl;

public class SPIRVOCLNativeDispatcher {

    public SPIRVOCLNativeDispatcher() {
    }

    native long clCreateProgramWithIL_native(long contextPointer, byte[] spirvBinaryCode, long[] lengths, int[] returnCode);

    native int clBuildProgram_native(long programId, int numDevices, long[] devices, String options);

    /**
     * Build the OpenCL Program using the SPIR-V Binary.
     * It requires OpenCL >= 2.1.
     *
     * @param contextPointer
     *     Pointer that represents the OpenCL Context.
     * @param spirvBinaryCode
     *     SPIR-V binary
     * @param lengths
     *     Length of the SPIR-V Binary
     * @return long: Program Pointer
     */
    public long clCreateProgramWithIL(long contextPointer, byte[] spirvBinaryCode, long[] lengths, int[] returnCode) {
        return clCreateProgramWithIL_native(contextPointer, spirvBinaryCode, lengths, returnCode);
    }

    /**
     * It builds an OpenCL Program
     *
     * @param programPointer
     *     program pointer
     * @param numDevices
     *     Number of OpenCL devices.
     * @param devices
     *     List of OpenCL Devices
     * @param options
     *     Options for Compilation.
     * @return status
     */
    public int clBuildProgram(long programPointer, int numDevices, long[] devices, String options) {
        return clBuildProgram_native(programPointer, numDevices, devices, options);
    }

    private native long clCreateKernel_native(long programPointer, String kernelName, int[] errorCode);

    /**
     * @param programPointer
     *     OpenCL Program Pointer
     * @param kernelName
     *     Kernel Name
     * @param errorCode
     *     Error code in position 0
     * @return long
     *     kernel pointer
     */
    public long clCreateKernel(long programPointer, String kernelName, int[] errorCode) {
        return clCreateKernel_native(programPointer, kernelName, errorCode);
    }

    private native String clGetProgramBuildInfo_native(long programPointer, long oclDevice);

    /**
     * @param programPointer
     *     OpeCL Program Pointer
     * @param devicePointer
     *     OpenCL Device Pointer
     * @return String
     *     Full Log
     */
    public String clGetProgramBuildInfo(long programPointer, long devicePointer) {
        return clGetProgramBuildInfo_native(programPointer, devicePointer);
    }

    private native int clSetKernelArg_native(long kernelPointer, int argIndex, int numBytes, long bufferPointer);

    /**
     * 
     * @param kernelPointer
     *     OpenCL kernel pointer
     * @param argIndex
     *     Argument Index
     * @param numBytes
     *     Number of Bytes for the argument
     * @param bufferPointer
     *     Buffer Pointer
     * @return int: status
     */
    public int clSetKernelArg(long kernelPointer, int argIndex, int numBytes, long bufferPointer) {
        return clSetKernelArg_native(kernelPointer, argIndex, numBytes, bufferPointer);
    }

    private native int clEnqueueNDRangeKernel_native(long queuePointer, long kernelPointer, int dimensions, long[] offset, long[] globalWorkGroup, long[] localWorkGroup, long[] waitEvents,
            long[] kernelEvent);

    /**
     * 
     * @param queuePointer
     *     OpenCL Command Queue Pointer
     * @param kernelPointer
     *     OpenCL kernel pointer
     * @param dimensions
     *     Parallel Dimensions of the Kernel (1, 2 or 3).
     * @param offset
     *     Global Offset
     * @param globalWorkGroup
     *     Global Work Size
     * @param localWorkGroup
     *     Local Work Size
     * @param waitEvents
     *     Array for the events to wait before the kernel launch
     * @param kernelEvent
     *     Kernel Event that will be return after the clEnqueueNDRangeKernel_native
     *
     * @return int: status
     */
    public int clEnqueueNDRangeKernel(long queuePointer, long kernelPointer, int dimensions, long[] offset, long[] globalWorkGroup, long[] localWorkGroup, long[] waitEvents, long[] kernelEvent) {
        return clEnqueueNDRangeKernel_native(queuePointer, kernelPointer, dimensions, offset, globalWorkGroup, localWorkGroup, waitEvents, kernelEvent);
    }
}
