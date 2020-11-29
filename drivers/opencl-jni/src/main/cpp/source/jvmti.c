#include <jni.h>
#include <jvmti.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
static jboolean checkJVMTIError(jvmtiEnv *jvmti, jvmtiError errNum, const char *str) {
   if (errNum != JVMTI_ERROR_NONE) {
      char *errnum_str= NULL;
      (void) (*jvmti)->GetErrorName(jvmti, errNum, &errnum_str);
      fprintf(stderr,"JVMTI: ERROR %d %s: %s \n", errNum,
            (errnum_str == NULL ? "Unknown error" : errnum_str), (str == NULL ? "" : str));
      return JNI_FALSE;
   }
   return JNI_TRUE;
}

static jrawMonitorID lock;

/* Enter agent monitor protected section */
static void enterAgentMonitor(jvmtiEnv *jvmti) {
   jvmtiError err = (*jvmti)->RawMonitorEnter(jvmti, lock);
   checkJVMTIError(jvmti, err, "raw monitor enter");
}

/* Exit agent monitor protected section */
static void exitAgentMonitor(jvmtiEnv *jvmti) {
   jvmtiError err = (*jvmti)->RawMonitorExit(jvmti, lock);
   checkJVMTIError(jvmti, err, "raw monitor exit");
}


static void JNICALL callbackExceptionEvent(jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jmethodID method, jlocation location,
      jobject exception, jmethodID catchMethod, jlocation catchLocation) {
   fprintf(stderr,"throw!\n");

}
static jint JNICALL callbackHeapIteration (jlong class_tag, jlong size, jlong* tag_ptr, jint length, void* user_data){
   fprintf(stderr,"heap CB!\n");
   return 0; // JVMTI_VISIT_OBJECTS || JVMTI_VISIT_ABORT
}

static void JNICALL garbageCollectionStart (jvmtiEnv *jvmti){
   fprintf(stderr,"gc START -------------------------------------------------\n");
}



static void JNICALL garbageCollectionFinish (jvmtiEnv *jvmti){
   fprintf(stderr,"gc END -------------------------------------------------\n");

   // cant do this like this.  need to create an agent thread and signal using monitors, 
   // see +http://hg.openjdk.java.net/jdk8u/jdk8u-dev/jdk/file/tip/src/share/demo/jvmti/heapViewer/heapViewer.c
   if (0){
      jvmtiHeapCallbacks heapCallbacks;
      (void) memset(&heapCallbacks, 0, sizeof(heapCallbacks));
      heapCallbacks.heap_iteration_callback = &callbackHeapIteration;
      jvmtiError error =  (*jvmti)->IterateThroughHeap(jvmti, /*(jint) heap_filter */0, /*(jclass) klass */ NULL, &heapCallbacks, /*(void*) user_data*/ NULL);
      checkJVMTIError(jvmti, error, "Cannot iterate heap ");
   }
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL *agentThread (jvmtiEnv* jvmti, JNIEnv* env, void* arg){
   while (1){
      fprintf(stderr,"in agent\n");
      sleep(1);
   }
}

static void JNICALL vmInit(jvmtiEnv *jvmti, JNIEnv *env, jthread thread) {
   //https://download-imagej.mpi-cbg.de/~curtis/src/apache-harmony-6.0-src-r991881/drlvm/vm/tests/jvmti/PopFrame1/popframe.cpp

   jclass klass = (*env)->FindClass(env, "java/lang/Thread");
   if(klass) {
      jmethodID method = (*env)->GetMethodID(env, klass, "<init>", "(Ljava/lang/String;)V");
      if (method){
         jstring name = (*env)->NewStringUTF(env, "AgentThread");
         if(name) {
            jthread newthread = (*env)->NewObject(env, klass, method, name);
            if (thread){
               jvmtiError err = (*jvmti)->RunAgentThread(jvmti, newthread, &agentThread,  NULL,JVMTI_THREAD_NORM_PRIORITY);
               checkJVMTIError(jvmti, err, "Cannot create agent thread");
            }else{
               fprintf(stderr, "failed to create new thread\n");
            }
         }else{
            fprintf(stderr, "failed to create thread name \n");
         }
      }else{
         fprintf(stderr, "failed to find constructor\n");
      }
   }else{
      fprintf(stderr, "failed to find Thread class\n");
   }


   enterAgentMonitor(jvmti);

   //    jvmtiError err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_DATA_DUMP_REQUEST, NULL);
   //   checkJVMTIError(jvmti, err, "set event notification");

   exitAgentMonitor(jvmti);
}

/* Callback for JVMTI_EVENT_VM_DEATH */
static void JNICALL vmDeath(jvmtiEnv *jvmti, JNIEnv *env) {

   /* Make sure everything has been garbage collected */
   jvmtiError err = (*jvmti)->ForceGarbageCollection(jvmti);
   checkJVMTIError(jvmti, err, "force garbage collection");

   /* Disable events and dump the heap information */
   enterAgentMonitor(jvmti);
   //  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_DISABLE, JVMTI_EVENT_DATA_DUMP_REQUEST, NULL);
   //  checkJVMTIError(jvmti, err, "set event notification");
   //dataDumpRequest(jvmti);
   //   gdata->vmDeathCalled = JNI_TRUE;
   exitAgentMonitor(jvmti);
}


/*
   useful tables from jvmti.h 

   Also check https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html
https://docs.oracle.com/en/java/javase/11/docs/specs/jvmti.html
https://github.com/jon-bell/bytecode-examples/blob/master/heapTagging/src/main/c/tagger.cpp
https://github.com/sachin-handiekar/jvmti-examples/blob/master/GetAllStackTraces/Native/library.cpp
https://alvinalexander.com/java/jwarehouse/openjdk-8/jdk/src/share/demo/jvmti/hprof/hprof_util.c.shtml

typedef struct {
unsigned int can_tag_objects : 1;
unsigned int can_generate_field_modification_events : 1;
unsigned int can_generate_field_access_events : 1;
unsigned int can_get_bytecodes : 1;
unsigned int can_get_synthetic_attribute : 1;
unsigned int can_get_owned_monitor_info : 1;
unsigned int can_get_current_contended_monitor : 1;
unsigned int can_get_monitor_info : 1;
unsigned int can_pop_frame : 1;
unsigned int can_redefine_classes : 1;
unsigned int can_signal_thread : 1;
unsigned int can_get_source_file_name : 1;
unsigned int can_get_line_numbers : 1;
unsigned int can_get_source_debug_extension : 1;
unsigned int can_access_local_variables : 1;
unsigned int can_maintain_original_method_order : 1;
unsigned int can_generate_single_step_events : 1;
unsigned int can_generate_exception_events : 1;
unsigned int can_generate_frame_pop_events : 1;
unsigned int can_generate_breakpoint_events : 1;
unsigned int can_suspend : 1;
unsigned int can_redefine_any_class : 1;
unsigned int can_get_current_thread_cpu_time : 1;
unsigned int can_get_thread_cpu_time : 1;
unsigned int can_generate_method_entry_events : 1;
unsigned int can_generate_method_exit_events : 1;
unsigned int can_generate_all_class_hook_events : 1;
unsigned int can_generate_compiled_method_load_events : 1;
unsigned int can_generate_monitor_events : 1;
unsigned int can_generate_vm_object_alloc_events : 1;
unsigned int can_generate_native_method_bind_events : 1;
unsigned int can_generate_garbage_collection_events : 1;
unsigned int can_generate_object_free_events : 1;
unsigned int can_force_early_return : 1;
unsigned int can_get_owned_monitor_stack_depth_info : 1;
unsigned int can_get_constant_pool : 1;
unsigned int can_set_native_method_prefix : 1;
unsigned int can_retransform_classes : 1;
unsigned int can_retransform_any_class : 1;
unsigned int can_generate_resource_exhaustion_heap_events : 1;
unsigned int can_generate_resource_exhaustion_threads_events : 1;
unsigned int : 7;
unsigned int : 16;
unsigned int : 16;
unsigned int : 16;
unsigned int : 16;
unsigned int : 16;
} jvmtiCapabilities;

typedef enum {
JVMTI_MIN_EVENT_TYPE_VAL = 50,
JVMTI_EVENT_VM_INIT = 50,
JVMTI_EVENT_VM_DEATH = 51,
JVMTI_EVENT_THREAD_START = 52,
JVMTI_EVENT_THREAD_END = 53,
JVMTI_EVENT_CLASS_FILE_LOAD_HOOK = 54,
JVMTI_EVENT_CLASS_LOAD = 55,
JVMTI_EVENT_CLASS_PREPARE = 56,
JVMTI_EVENT_VM_START = 57,
JVMTI_EVENT_EXCEPTION = 58,
JVMTI_EVENT_EXCEPTION_CATCH = 59,
JVMTI_EVENT_SINGLE_STEP = 60,
                        JVMTI_EVENT_FRAME_POP = 61,
                        JVMTI_EVENT_BREAKPOINT = 62,
                        JVMTI_EVENT_FIELD_ACCESS = 63,
                        JVMTI_EVENT_FIELD_MODIFICATION = 64,
                        JVMTI_EVENT_METHOD_ENTRY = 65,
                        JVMTI_EVENT_METHOD_EXIT = 66,
                        JVMTI_EVENT_NATIVE_METHOD_BIND = 67,
                        JVMTI_EVENT_COMPILED_METHOD_LOAD = 68,
                        JVMTI_EVENT_COMPILED_METHOD_UNLOAD = 69,
                        JVMTI_EVENT_DYNAMIC_CODE_GENERATED = 70,
                        JVMTI_EVENT_DATA_DUMP_REQUEST = 71,
                        JVMTI_EVENT_MONITOR_WAIT = 73,
                        JVMTI_EVENT_MONITOR_WAITED = 74,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTER = 75,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTERED = 76,
                        JVMTI_EVENT_RESOURCE_EXHAUSTED = 80,
                        JVMTI_EVENT_GARBAGE_COLLECTION_START = 81,
                        JVMTI_EVENT_GARBAGE_COLLECTION_FINISH = 82,
                        JVMTI_EVENT_OBJECT_FREE = 83,
                        JVMTI_EVENT_VM_OBJECT_ALLOC = 84,
                        JVMTI_MAX_EVENT_TYPE_VAL = 84
                        } jvmtiEvent;

*/

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {

   jvmtiEnv *jvmti;
   jvmtiCapabilities capabilities;

   jvmtiError error;
   jint result;

   jvmtiEventCallbacks eventCallbacks;

   result = (*jvm)->GetEnv(jvm, (void **) &jvmti, JVMTI_VERSION);

   if (result != JNI_OK) {
      fprintf(stderr,"\n Unable to access JVMTI!!!\n");
   }
   fprintf(stderr,"\nagent loaded OK \n");


   (void) memset(&capabilities, 0, sizeof(jvmtiCapabilities));

   capabilities.can_tag_objects = 1;
   capabilities.can_generate_exception_events = 1;
   capabilities.can_access_local_variables = 1;
   capabilities.can_get_constant_pool = 1;
   capabilities.can_get_bytecodes = 1;
   capabilities.can_generate_garbage_collection_events = 1;

   error = (*jvmti)->AddCapabilities(jvmti, &capabilities);
   checkJVMTIError(jvmti, error, "Unable to set Capabilities");

   error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, (jthread) NULL);
   checkJVMTIError(jvmti, error, "Cannot set Exception Event Notification");
   error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_GARBAGE_COLLECTION_START, (jthread) NULL);
   checkJVMTIError(jvmti, error, "Cannot set GC Start Event Notification");
   error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_GARBAGE_COLLECTION_FINISH, (jthread) NULL);
   checkJVMTIError(jvmti, error, "Cannot set GC Start Event Notification");
   error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, (jthread) NULL);
   checkJVMTIError(jvmti, error, "Cannot set VM Init Notification");
   error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, (jthread) NULL);
   checkJVMTIError(jvmti, error, "Cannot set VM Death Notification");

   (void) memset(&eventCallbacks, 0, sizeof(eventCallbacks));
   eventCallbacks.Exception = &callbackExceptionEvent;
   eventCallbacks.GarbageCollectionStart = &garbageCollectionStart;
   eventCallbacks.GarbageCollectionFinish = &garbageCollectionFinish;
   eventCallbacks.VMDeath = &vmDeath;
   eventCallbacks.VMInit = &vmInit;


   error = (*jvmti)->SetEventCallbacks(jvmti, &eventCallbacks, (jint) sizeof(eventCallbacks));
   checkJVMTIError(jvmti, error, "Cannot set Event Callbacks.");


   error = (*jvmti)->CreateRawMonitor(jvmti, "JVMTI Agent Data", &lock);
   checkJVMTIError(jvmti, error, "Cannot create lock");


   return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
   fprintf(stderr,"\nagent unloaded OK \n");
}

JNIEXPORT void JNI_Onload(JavaVM *vm, void *reserved){
   fprintf(stderr,"\njni loaded OK \n");
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved){
   fprintf(stderr,"\njni unloaded OK \n");
}
