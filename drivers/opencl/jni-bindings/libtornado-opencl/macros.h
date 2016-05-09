#define VERBOSE 0

#define debug(fmt,args...) if(VERBOSE) printf(fmt,args);

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
