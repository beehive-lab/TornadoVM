//
// Created by juan on 07/12/2020.
//

#ifndef TORNADO_ZE_LOG_H
#define TORNADO_ZE_LOG_H

#define LOG_JNI 1

#define CHECK_ERROR(status)            \
    if (status != ZE_RESULT_SUCCESS) { \
        std::cout << "Error in line: " \
                  << __LINE__          \
                  << " Function: "     \
                  << __FUNCTION__      \
                  << std::endl;        \
    }

#define LOG_ZE_JNI(name, result)                        \
    if (LOG_JNI == 1)  {                                \
        std::cout << "[tornado-JNI] Calling : " << name \
        << " -> Status: " << result                     \
        << std::endl;                                   \
    }                                                   \

#endif
