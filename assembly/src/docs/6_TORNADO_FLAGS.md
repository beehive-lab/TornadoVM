# TornadoVM Flags


There is a number of runtime flags and compiler flags to enable experimental features, as well as fine and coarse grain profilling in the context of TornadoVM.


**Note:** for the following examples ```s0``` represents an arbitrary schedule, as well as ```t0``` represents a given task's name.   


All flags needs Java prefix of ```-D```. An example of tornado using a flag is the following:  


```$ tornado -Dtornado.fullDebug=true uk.ac.manchester.examples.compute.Montecarlo 1024```  


## List of TornadoVM Flags:

* ```-Dtornado.fullDebug=true ```:  
Enables full debugging log to be output in the. command line.  

* `` --printKernel ``:  
Print the generated OpenCL kernel in the command line.

* ```--debug ```:  
Print minor debug in fo such as number of parallel threads used.

* ```--devices```:  
Output a list of all OpenCL-ready devices on the current system.

* ```-Dtornado.ns.time=true ```:  
 Converts the time to units to nanoseconds instead of milliseconds. 
  
* ```-Dtornado.debug.compiletimes=true ```:  
Print method compiliation times.

* ```-Ds0.t0.global.dims=XXX,XXX```:  
Allows to define global worksizes (problem sizes).

* ```-Ds0.t0.local.dims=XXX,XXX```:  
Allows to define custom local workgroum configuration and overwrite the default values provided by the TornadoScheduler.  

* ```-Dtornado.profiling.enable=true ```:  
Enable profilling for OpenCL events such as kernel times and data tranfers. 
 
* ```-Dtornado.opencl.userelative=true ```:  
Enables use of relative addresses which a prior for using DMA tranfers for Alters/Intel FPGAs. 
 
* ```-Dtornado.opencl.timer.kernel=true ```:  
Print kernel times for OpenCL compute kernels.

* ```-Dtornado.precompiled.binary=PATH```:
 Provides the location of the bistream or pre-geneared OpenCL (.cl) kernel. 
 
* ```-Dtornado.fpga.conf.file=FILE```:
 Provides the absolute path of the FPGA configuation file. 
 
* ```-Dtornado.opencl.blocking=true```:  
Allows to force API blocking calls. 

* `-Dtornado.profiler=True`:  
It enables profiler information such as `COPY_IN`, `COPY_OUT`, compilation time, total time, etc. This flag is disabled by default.

* `-Dtornado.opencl.compiler.options=LIST_OF_OPTIONS`:  
It allows to pass the compile options specified by the OpenCL ``CLBuildProgram`` [specification](https://www.khronos.org/registry/OpenCL/sdk/1.0/docs/man/xhtml/clBuildProgram.html) to TornadoVM at runtime. By default it doesn't enable any. 

