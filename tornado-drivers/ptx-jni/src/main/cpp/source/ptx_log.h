/*
 * MIT License
 *
 * Copyright (c) 2020-2022, 2024, APT Group, Department of Computer Science,
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
#ifndef TORNADO_PTX_LOG_H
#define TORNADO_PTX_LOG_H

#include <cuda.h>
#define LOG_PTX 0

#define LOG_PTX_AND_VALIDATE(name, result)                      \
    if (LOG_PTX == 1)  {                                        \
        std::cout << "[TornadoVM-PTX-JNI] Calling : " << name   \
        << " -> Status: " << result                             \
        << std::endl;                                           \
    }                                                           \
    if (result != CUDA_SUCCESS)  {                              \
        std::cout << "\t[TornadoVM-PTX-JNI] ERROR : " << name   \
        << " -> Returned: " << result                           \
        << std::endl;                                           \
    }


#define LOG_NVML_AND_VALIDATE(name, result)                         \
    if (LOG_PTX == 1)  {                                            \
        std::cout << "[TornadoVM-PTX-NVML-JNI] Calling : " << name  \
        << " -> Status: " << result                                 \
        << std::endl;                                               \
    }                                                               \
    if (result != NVML_SUCCESS)  {                                  \
        std::cout << "[TornadoVM-PTX-NVML-JNI] ERROR : " << name    \
        << " -> Returned: " << result                               \
        << std::endl;                                               \
    }
#endif
