#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif

//extern jmethodID driver_callback_id;

char *getOpenCLError(char *,cl_int);

void resetAndStartTimer( );

unsigned long long getElapsedTime( );
