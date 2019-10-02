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

The option `-Dtornado.log.profiler=True` prints a full report only when the method `ts.getProfileLog` is called.


