//
// Created by juan on 07/12/2020.
//

#ifndef TORNADO_ZE_LOG_H
#define TORNADO_ZE_LOG_H

#define LOG_JNI 1

#define RED "\033[1;31m"
#define GREEN "\033[32m"
#define MAGENTA "\033[35m"
#define CYAN "\033[36m"
#define RESET "\033[0m"

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
        std::cout << MAGENTA << "[tornado-JNI] "                  \
        << RESET                                                  \
        << " Calling : " CYAN << name                             \
        << GREEN                                                  \
        << " -> Status: " << result                               \
        << RESET                                                  \
        << std::endl;                                             \
    }

#endif
