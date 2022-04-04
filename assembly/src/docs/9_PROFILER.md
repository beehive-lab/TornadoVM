# TornadoVM Profiler

To enable the TornadoVM profiler use `--enableProfiler <silent|console>`.

* `console`: It prints a JSON entry for each task-schedule executed via STDOUT.
* `silent` : It enables profiling information in silent mode. Use the profiler API to query the values. 

Example:

```bash
$ tornado --enableProfiler console  -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt 100000
{
    "s0": {
        "COPY_OUT_TIME": "36576",
        "TOTAL_TASK_SCHEDULE_TIME": "104699731",
        "TOTAL_GRAAL_COMPILE_TIME": "36462460",
        "TOTAL_KERNEL_TIME": "25600",
        "DISPATCH_TIME": "61952",
        "COPY_IN_TIME": "88288",
        "TOTAL_DRIVER_COMPILE_TIME": "710824",
        "TOTAL_BYTE_CODE_GENERATION": "7031446",
        "s0.t0": {
            "DEVICE_ID": "0:2",
            "DEVICE": "GeForce GTX 1650",
            "TASK_COPY_OUT_SIZE_BYTES": "8216",
            "TASK_COPY_IN_SIZE_BYTES": "32892",
            "TASK_COMPILE_GRAAL_TIME": "36462460",
            "TASK_COMPILE_DRIVER_TIME": "710824",
            "TASK_KERNEL_TIME": "25600"
        }
    }
}
```

All timers are printed in nanoseconds.


#### Explanation

* *COPY_IN_TIME*: OpenCL timers for copy in (host to device)
* *COPY_OUT_TIME*: OpenCL timers for copy out (device to host)
* *DISPATCH_TIME*: time spent for dispatching a submitted OpenCL command
* *TOTAL_KERNEL_TIME*: It is the sum of all OpenCL kernel timers. For example, if a task-schedule contains 2 tasks, this timer reports the sum of execution of the two kernels.
* *TOTAL_BYTE_CODE_GENERATION*: time spent in the Tornado bytecode generation
* *TOTAL_TASK_SCHEDULE_TIME*: Total execution time. It contains all timers
* *TOTAL_GRAAL_COMPILE_TIME*: Total compilation with Graal (from Java to OpenCL C / PTX)
* *TOTAL_DRIVER_COMPILE_TIME*: Total compilation with the driver (once the OpenCL C / PTX code is generated, the time that the driver takes to generate the final binary).


Then, for each task within a task-schedule, there are usually three timers, one device identifier and two data transfer metrics:

* *DEVICE_ID*: platform and device ID index.
* *DEVICE*: device name as provided by the OpenCL driver.
* *TASK_COPY_IN_SIZE_BYTES*: size in bytes of total bytes copied-in for a given task.
* *TASK_COPY_OUT_SIZE_BYTES*: size in bytes of total bytes copied-out for a given task.
* *TASK_COMPILE_GRAAL_TIME*: time that takes to compile a given task with Graal.
* *TASK_COMPILE_DRIVER_TIME*: time that takes to compile a given task with the OpenCL/CUDA driver.
* *TASK_KERNEL_TIME*: kernel execution for the given task (Java method).


#### Note

When the task-schedule is executed multiple times, timers related to compilation will not appear in the Json time-report. This is because the generated binary is cached and there is no compilation after the second iteration.


### Print timers at the end of the execution

The options `--enableProfiler silent` print a full report only when the method `ts.getProfileLog()` is called.

### Save profiler into a file

Use the option `--dumpProfiler <FILENAME>` to store the profiler output in a JSON file.

### Parsing Json files

TornadoVM creates the `profiler-app.json` file with multiple entries for the application (one per task-schedule invocation).

TornadoVM's distribution includes a set of utilities for parsing and obtaining statistics:

```bash
$ createJsonFile.py profiler-app.json output.json
$ readJsonFile.py output.json

['readJsonFile.py', 'output.json']
Processing file: output.json
Num entries = 10
Entry,0
    TOTAL_BYTE_CODE_GENERATION,6783852
    TOTAL_KERNEL_TIME,26560
    TOTAL_TASK_SCHEDULE_TIME,59962224
    COPY_OUT_TIME,32768
    COPY_IN_TIME,81920
    TaskName, s0.t0
    TASK_KERNEL_TIME,26560
    TASK_COMPILE_DRIVER_TIME,952126
    TASK_COMPILE_GRAAL_TIME,46868099
    TOTAL_GRAAL_COMPILE_TIME,46868099
    TOTAL_DRIVER_COMPILE_TIME,952126
    DISPATCH_TIME,31008
    EndEntry,0

MEDIANS    ### Print median values for each timer
    TOTAL_KERNEL_TIME,25184.0
    TOTAL_TASK_SCHEDULE_TIME,955967.0
    s0.t0-TASK_KERNEL_TIME,25184.0
    COPY_IN_TIME,74016.0
    COPY_OUT_TIME,32816.0
    DISPATCH_TIME,31008.0
```


### Task-Schedule API augmented with profiling calls

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

    long getDispatchTime();

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

// Query copy-out total time (from Device to Host)
long copyOutTime = schedule.getDeviceReadTime();

// Query dispatch time
long dispatchTime = schedule.getDispatchTime();

// Query total kernel time
long kernelTime = schedule.getDeviceKernelTime();

// Query total compilation time
long compilationTime = schedule.getCompileTime();
```


## Code feature extraction for the OpenCL/PTX generated code

To enable TornadoVM's code feature extraction, use the following flag: `-Dtornado.feature.extraction=True`.

Example:

```bash
$ tornado -Dtornado.feature.extraction=True -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody 1024 1
$ cat tornado-features.json
{
    "nBody": {
        "DEVICE_ID": "0:2",
        "DEVICE": "GeForce GTX 1650",
        "Global Memory Loads":  "15",
        "Global Memory Stores":  "6",
        "Constant Memory Loads":  "0",
        "Constant Memory Stores":  "0",
        "Local Memory Loads":  "0",
        "Local Memory Stores":  "0",
        "Private Memory Loads":  "20",
        "Private Memory Stores":  "20",
        "Total Loops":  "2",
        "Parallel Loops":  "1",
        "If Statements":  "2",
        "Integer Comparison":  "2",
        "Float Comparison":  "0",
        "Switch Statements":  "0",
        "Switch Cases":  "0",
        "Vector Operations":  "0",
        "Integer & Float Operations":  "57",
        "Boolean Operations":  "9",
        "Cast Operations":  "2",
        "Float Math Functions":  "1",
        "Integer Math Functions":  "0"
    }
}

```
### Save features into a file

Use the option `-Dtornado.feature.extraction=True` `-Dtornado.features.dump.dir=FILENAME`.  `FILENAME` can contain the filename and the full path (e.g. features.json).

### Send log over a socket.

TornadoVM allows redirecting profiling and feature extraction logs to a specific port. This feature can be enabled with the option `-Dtornado.dump.to.ip=IP:PORT`.  
The following example redirects the profiler output to the localhost (127.0.0.1) and to a specified open port (2000):
```bash
$ tornado -Dtornado.profiler=True  -Dtornado.dump.to.ip=127.0.0.1:2000 -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt 100000
```
To test that the socket streams the logs correctly, open a local server in a different terminal with the following command:
```bash
$ ncat -k -l 2000
```
