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

#ifndef TORNADO_ZE_LOG_H
#define TORNADO_ZE_LOG_H

#define LOG_JNI 0

#define RED "\033[1;31m"
#define GREEN "\033[32m"
#define MAGENTA "\033[35m"
#define CYAN "\033[36m"
#define RESET "\033[0m"
#define LOG_JNI_SCOPE "[TornadoVM-SPIRV-JNI] "

#define CHECK_ERROR(status)            \
    if (status != ZE_RESULT_SUCCESS) { \
        std::cout << "Error in line: " \
                  << __LINE__          \
                  << " Function: "     \
                  << __FUNCTION__      \
                  << std::endl;        \
    }

#define LOG_ZE_JNI(name, result)                                  \
    if (LOG_JNI == 1)  {                                          \
        std::cout << MAGENTA <<  LOG_JNI_SCOPE                    \
        << RESET                                                  \
        << " Calling : " CYAN << name                             \
        << GREEN                                                  \
        << " -> Status: " << result                               \
        << RESET                                                  \
        << std::endl;                                             \
    }

#endif
