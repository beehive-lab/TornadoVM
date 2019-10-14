# Tornado Profiler

To enable the TornadoVM profiler use `-Dtornado.profiler=True`.


Example:

```bash
$ tornado -Dtornado.profiler=True  uk.ac.manchester.tornado.examples.VectorAddInt 100000
{
    "s0": {
        "COPY_OUT_TIME": "36576",
        "TOTAL_TASK_SCHEDULE_TIME": "104699731",
        "TOTAL_GRAAL_COMPILE_TIME": "36462460",
        "TOTAL_KERNEL_TIME": "25600",
        "COPY_IN_TIME": "88288",
        "TOTAL_DRIVER_COMPILE_TIME": "710824",
        "TOTAL_BYTE_CODE_GENERATION": "7031446",
        "s0.t0": {
            "TASK_COMPILE_GRAAL_TIME": "36462460",
            "TASK_COMPILE_DRIVER_TIME": "710824",
            "TASK_KERNEL_TIME": "25600"
        }
    }
}
```

All timers are printed in nanoseconds. 



### Explanation

* COPY_IN_TIME: OpenCL timers for copy in (host to device)
* COPY_OUT_TIME: OpenCL timers for copy out (device to host)
* TOTAL_KERNEL_TIME: It is the sum of all OpenCL kernel timers. For example, if a task-schedule contains 2 tasks, this timer reports the sum of execution of the two kernels.
* TOTAL_BYTE_CODE_GENERATION: time spent in the Tornado bytecode generation
* TOTAL_TASK_SCHEDULE_TIME: Total execution time. It contains all timers
* TOTAL_GRAAL_COMPILE_TIME: Total compilation with Graal (from Java to OpenCL C)
* TOTAL_DRIVER_COMPILE_TIME: Total compilation with the driver (once the OpenCL C code is generated, the time that the driver takes to generate the final binary, such as the PTX for NVIDIA).


Then, for each task within a task-schedule, there are usually three timers:

* TASK_COMPILE_GRAAL_TIME: time that takes to compile a given task with Graal.
* TASK_COMPILE_DRIVER_TIME: time that takes to compile a given task with the OpenCL driver.
* TASK_KERNEL_TIME: kernel execution for the given task (Java method).



### Note

When the task-schedule is executed multiple times, timers related to compilation will not appear in the Json time-report. This is because the generated binary is cached and there is no compilation after the second iteration. 



### Print timers at the end of the execution

The options `-Dtornado.profiler=True -Dtornado.log.profiler=True` print a full report only when the method `ts.getProfileLog` is called.


### Save profiler into a file

Use the option `-Dtornado.profiler=True -Dtornado.profiler.save=True`.  This option is set to `False` by default.


### Code feature extraction for the OpenCL generated code

To enable TornadoVM's code feature extraction, use the following flag: `-Dtornado.feature.extraction=True`. This will generate a Json file in the local directory called `tornado-features.json`.


Example:


```bash
$ tornado -Dtornado.feature.extraction=True uk.ac.manchester.tornado.examples.VectorAddInt 4096
$ cat tornado-features.json 
{
    "sketch-vectorAdd": { 
        "Global Memory Reads":  "2",
        "Global Memory Writes":  "1",
        "Local Memory Reads":  "0",
        "Local Memory Writes":  "0",
        "Total number of Loops":  "1",
        "Parallel Loops":  "1",
        "If Statements":  "0",
        "Switch Statements":  "0",
        "Switch Cases":  "0",
        "Vector Loads":  "0",
        "Arithmetic Operations":  "4",
        "Math Operations":  "0"
    }
}
```

## Task-Schedule API augmented with profile calls

TornadoVM Task-Schedules have a set of methods to query profile metrics such as kernel time, data transfers and compilation time. 

```java
public interface ProfileInterface {

    long getTotalTime();

    long getCompileTime();

    long getTornadoCompilerTime();

    long getDriverInstallTime();

    long getDataTransfersTime();

    long getWriteTime();

    long getReadTime();

    long getDeviceWriteTime();

    long getDeviceKernelTime();

    long getDeviceReadTime();

    String getProfileLog();
}
```

Example:

```java
 TaskSchedule schedule = new TaskSchedule("s0")
                .streamIn(a, b)
                .task("t0", Sample::methodToRun, a, b, c)
                .streamOut(c);

// Query copy-in time (from Host to Device)
long copyInTime = schedule.getDeviceWriteTime();
long copyOutTime = schedule.getDeviceReadTime();
long kernelTime = schedule.getDeviceKernelTime();
long compilationTime = schedule.getCompileTime();
```



