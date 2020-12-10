/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson, Juan Fumero
 *
 */
#ifndef macros_h
#define macros_h


#define VERBOSE 0

#define debug(fmt,args...) if(VERBOSE) printf(fmt,args);
#define warn(fmt,args...)  printf(fmt,args);

#define OCLEXCEPTION "uk/ac/manchester/tornado/drivers/opencl/exceptions/OCLException"

#define OPENCL_PROLOGUE cl_int error_id; \
    jclass cls;

#define OPENCL_ERROR(name,func,rc) if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    error_id = func; \
    if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
    } \
    if(error_id != CL_SUCCESS){ \
        cls = (*env)->FindClass(env,OCLEXCEPTION); \
        if(cls == NULL){ \
            return rc; \
        } \
        (*env)->ThrowNew(env,cls,getOpenCLError(name,error_id)); \
        (*env)->DeleteLocalRef(env,cls); \
        return rc; \
    }

#define OPENCL_SOFT_ERROR(name,func,rc) if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    error_id = func; \
    if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
    } \

#define OPENCL_CHECK_ERROR(name,func,rc) if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    func; \
    if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
    } \
    if(error_id != CL_SUCCESS){ \
        cls = (*env)->FindClass(env,OCLEXCEPTION); \
        if(cls == NULL){ \
            return rc; \
        } \
        (*env)->ThrowNew(env,cls,getOpenCLError(name,error_id)); \
        (*env)->DeleteLocalRef(env,cls); \
        return rc; \
    }

#define OPENCL_WRAP_CHECK_ERROR(name,value,func,rc) if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    value = func; \
    if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
    } \
    if(error_id != CL_SUCCESS){ \
        cls = (*env)->FindClass(env,OCLEXCEPTION); \
        if(cls == NULL){ \
            return rc; \
        } \
        (*env)->ThrowNew(env,cls,getOpenCLError(name,error_id)); \
        (*env)->DeleteLocalRef(env,cls); \
        return rc; \
    }

#define JNI_ACQUIRE_ARRAY(TYPE,VAR,ARR) TYPE *VAR = (*env)->GetPrimitiveArrayCritical(env, ARR, NULL)
#define JNI_ACQUIRE_ARRAY_OR_NULL(TYPE,VAR,ARR) TYPE *VAR = ( ARR != NULL) ? (*env)->GetPrimitiveArrayCritical(env, ARR, NULL) : NULL

#define JNI_RELEASE_ARRAY(array, variable) if (array != NULL) { (*env)->ReleasePrimitiveArrayCritical(env, array, variable, JNI_ABORT); }

#define OPENCL_DECODE_WAITLIST(array, events, num_events) \
    jlong *__ ## array = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL; \
    jlong *events = (array != NULL) ? &__ ## array[1] : NULL; \
    jsize num_events = (array != NULL) ? __ ## array[0] : 0;


#define OPENCL_RELEASE_WAITLIST(ARR) JNI_RELEASE_ARRAY(ARR, __ ## ARR)

#endif