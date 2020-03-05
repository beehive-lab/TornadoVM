List of tested drivers for running OpenCL with TornadoVM

## Intel 


##### Drivers from Intel Compute HD Graphics (Intel Compute Runtime) - NEO

[Link](https://github.com/intel/compute-runtime/releases)

* 20.07.15711: OK
* 20.06.15619: OK
* 20.04.15428: OK
* 20.03.15346: OK
* 20.02.15268: OK
* 20.01.15264: OK
* 19.49.15055: OK
* 19.48.14977: OK
* 19.47.14903: OK
* 19.43.14583: OK
* 19.23.13131: OK

##### Drivers for Intel FPGAs

[Link](http://fpgasoftware.intel.com/17.1/?edition=lite)

* 17.1 [OK]

##### Intel CPU

[Link](https://software.intel.com/en-us/articles/opencl-drivers)

* 18.1.0.0920 [OK]
* 1.2.0.37  (Old version for CentOS) [OK]

## NVIDIA 

##### NVIDIA GPUs

[Link](https://www.nvidia.com/Download/index.aspx?lang=en-us)

* 440.64: OK
* 440.59: OK
* 440.40: OK
* 440.36: OK 
* 435.21: OK 

## AMD

[Link](https://www.amd.com/en/support)

* 2766.4 (PAL,HSAIL) [OK]

## Xilinx

[Link](https://www.xilinx.com/products/design-tools/software-zone/sdaccel.html)

* 1.0, xocc v2018.2 [OK]

## Known Driver Issues 

* Intel CPU OpenCL driver `18.1.0.0920` is not working with Ubuntu >= 19.04
