#!/bin/bash

if [ $1 == 1 ]; then
	#Enable data transfer timer
	sed -i 's/#define PRINT_DATA_TIMES 0/#define PRINT_DATA_TIMES 1/g' drivers/opencl-jni/src/main/cpp/source/data_movers.c
	#Enable kernel timer
	sed -i 's/#define PRINT_KERNEL_EVENTS 0/#define PRINT_KERNEL_EVENTS 1/g' drivers/opencl-jni/src/main/cpp/source/OCLCommandQueue.c
else
	#Disable data transfer timer
	sed -i 's/#define PRINT_DATA_TIMES 1/#define PRINT_DATA_TIMES 0/g' drivers/opencl-jni/src/main/cpp/source/data_movers.c
	#Disable kernel timer
	sed -i 's/#define PRINT_KERNEL_EVENTS 1/#define PRINT_KERNEL_EVENTS 0/g' drivers/opencl-jni/src/main/cpp/source/OCLCommandQueue.c
fi
