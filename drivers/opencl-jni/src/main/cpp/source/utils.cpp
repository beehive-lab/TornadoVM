/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
#include "utils.h"
#include <string>
#include <iostream>
#include <sstream>

char *getOpenCLError(char *func, cl_int code) {
    std::string str;
    switch (code) {
        case CL_SUCCESS:
            str = "Operation completed successfully.";
            break;
        case CL_INVALID_VALUE:
            str = "CL_INVALID_VALUE";
            break;
        case CL_INVALID_DEVICE:
            str = "CL_INVALID_DEVICE";
            break;
        case CL_DEVICE_NOT_AVAILABLE:
            str = "CL_DEVICE_NOT_AVAILABLE";
            break;
        case CL_OUT_OF_HOST_MEMORY:
            str = "CL_OUT_OF_HOST_MEMORY";
            break;
        case CL_INVALID_CONTEXT:
            str = "CL_INVALID_CONTEXT";
            break;
        case CL_INVALID_MEM_OBJECT:
            str = "CL_INVALID_MEM_OBJECT";
            break;
        default:
            str = "Unknown OpenCL Error";
    }
    std::stringstream outString;
    outString << "function" << std::string(func) << " ("  << code <<  "): " << str;
    return const_cast<char *>(outString.str().c_str());
}


