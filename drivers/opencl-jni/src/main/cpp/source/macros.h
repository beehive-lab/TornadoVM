/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
#define VERBOSE 0

#define debug(fmt,args...) if(VERBOSE) printf(fmt,args);
#define warn(fmt,args...)  printf(fmt,args);

#define OCLEXCEPTION "tornado/drivers/opencl/exceptions/OCLException"

#define OPENCL_PROLOGUE cl_int error_id; \
    jclass cls;

#define OPENCL_ERROR(name,func,rc) if(VERBOSE) {\
        printf("tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    error_id = func; \
    if(VERBOSE) {\
        printf("tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
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
        printf("tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    error_id = func; \
    if(VERBOSE) {\
        printf("tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
    } \
    if(error_id != CL_SUCCESS){ \
        return rc; \
    }

#define OPENCL_CHECK_ERROR(name,func,rc) if(VERBOSE) {\
        printf("tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    func; \
    if(VERBOSE) {\
        printf("tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
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
        printf("tornado.drivers.opencl> Calling: %s\n",name); \
    } \
    value = func; \
    if(VERBOSE) {\
        printf("tornado.drivers.opencl> Returned: %s = %d\n",name,error_id); \
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