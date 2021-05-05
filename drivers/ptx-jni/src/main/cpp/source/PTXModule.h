/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

#include <jni.h>
#include <cuda.h>
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXModule */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule
#ifdef __cplusplus
extern "C" {
#endif

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadData
        (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleUnload
 * Signature: ([B)J
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleUnload
        (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuOccupancyMaxPotentialBlockSize
 * Signature: ([BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuOccupancyMaxPotentialBlockSize
        (JNIEnv *, jclass, jbyteArray, jstring);

#ifdef __cplusplus
}
#endif
#endif