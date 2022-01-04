# TornadoVM Flags


There is a number of runtime flags and compiler flags to enable experimental features, as well as fine and coarse grain profilling in the context of TornadoVM.


**Note:** for the following examples ```s0``` represents an arbitrary schedule, as well as ```t0``` represents a given task's name.   


All flags needs Java prefix of ```-D```. An example of tornado using a flag is the following:  


```$ tornado -Dtornado.fullDebug=true uk.ac.manchester.examples.compute.Montecarlo 1024```  


## List of TornadoVM Flags:

* ```-Dtornado.fullDebug=true ```:  
Enables full debugging log to be output in the. command line.  

* `` --printKernel ``:  
Print the generated OpenCL/PTX kernel in the command line.

* ```--threadInfo```:  
Print the information about the number of parallel threads used.

* ```--debug```:  
Print minor debug info.

* ```--devices```:  
Output a list of all available devices on the current system.

* ```-Dtornado.ns.time=true ```:  
 Converts the time to units to nanoseconds instead of milliseconds.

* ```-Dtornado.{ptx,opencl}.priority=X ```:
Allows to define a driver priority. The drivers are sorted in descending order based on their priority. By default, the `PTX driver` has priority `1` and the `OpenCL driver` has priority `0`.

* ```-Ds0.t0.global.dims=XXX,XXX```:  
Allows to define global worksizes (problem sizes).

* ```-Ds0.t0.local.dims=XXX,XXX```:  
Allows to define custom local workgroum configuration and overwrite the default values provided by the TornadoScheduler.  

* ```-Dtornado.profiling.enable=true ```:  
Enable profilling for OpenCL/CUDA events such as kernel times and data tranfers.

* ```-Dtornado.opencl.userelative=true ```:  
Enable usage of relative addresses which is a prerequisite for using DMA tranfers on Altera/Intel FPGAs. Nonetheless, this flag can be used for any OpenCL device.

* ```-Dtornado.precompiled.binary=PATH```:
 Provides the location of the bistream or pre-generated OpenCL (.cl) kernel.

* ```-Dtornado.fpga.conf.file=FILE```:
 Provides the absolute path of the FPGA configuation file.

* ```-Dtornado.fpgaDumpLog=true```:
 Dumps the log information from the HLS compilation to the command prompt.

* ```-Dtornado.opencl.blocking=true```:  
Allows to force OpenCL API blocking calls.

* `--enableProfiler console`:  
It enables profiler information such as `COPY_IN`, `COPY_OUT`, compilation time, total time, etc. This flag is disabled by default. TornadoVM will print by STDOUT a JSON string containing all profiler metrics related to the execution of each task-schedule. 

* `--enableProfiler silent`:  It enables profiler information such as `COPY_IN`, `COPY_OUT`, compilation time, total time, etc. This flag is disabled by default. The profiler information is stored internally and it can be queried using the [TornadoVM Profiler API](https://github.com/beehive-lab/TornadoVM/blob/master/tornado-api/src/main/java/uk/ac/manchester/tornado/api/profiler/ProfileInterface.java).

* `--dumpProfiler FILENAME`:  
It enables profiler information such as `COPY_IN`, `COPY_OUT`, compilation time, total time, etc. This flag is disabled by default. TornadoVM will save the profiler information in the `FILENAME` after the execution of each task-schedule.

* `-Dtornado.opencl.compiler.options=LIST_OF_OPTIONS`:  
It allows to pass the compile options specified by the OpenCL ``CLBuildProgram`` [specification](https://www.khronos.org/registry/OpenCL/sdk/1.0/docs/man/xhtml/clBuildProgram.html) to TornadoVM at runtime. By default it doesn't enable any.

##### Optimizations

* `-Dtornado.enable.fma=True`:  
It enables Fused-Multiply-Add optimizations. This option is enabled by default. However, for some platforms, such as the Xilinx FPGA using SDAccel 2018.2 and OpenCL 1.0, this option must be disabled as it causes runtime errors. See issue on [Github](https://github.com/beehive-lab/TornadoVM/issues/24).

* `-Dtornado.enable.mathOptimizations`: It enables math optimizations. For instance, `1/sqrt(x)` is transformed into `rsqrt` instruction for the corresponding backend (OpenCL, SPIRV and PTX). It is enabled by default. 

* `-Dtornado.experimental.partial.unroll=True`:
It enables the compiler to force partial unroll on counted loops with a factor of 2. The unroll factor can be configured with the `tornado.partial.unroll.factor=FACTOR` that the FACTOR value can take integer values up to 32.

* `-Dtornado.enable.nativeFunctions=False`:
It enables the utilization of native mathematical functions, in case that the selected backend (OpenCL, PTX, SPIR-V) suports native functions. This option is disabled by default.
