# List of Tested OpenCL Drivers for TornadoVM

## OpenCL supported drivers for OSx

* 1.2 (Mar 15 2020)
  * AMD Radeon Pro 560 Compute Engine
  * Intel(R) HD Graphics 630
  * Intel(R) Core(TM) i7-7820HQ CPU

* 1.2 (Jan 23 2020)
   * Intel(R) Core(TM) i5-5257U CPU
   * Intel(R) Iris(TM) Graphics 6100

## List of supported drivers for Linux

### Intel

##### Drivers from Intel Compute HD Graphics (Intel Compute Runtime) - NEO

[Link](https://github.com/intel/compute-runtime/releases)

The following drivers have been tested on Linux >= CentOS 7.3

* 21.24.20098: OK  ( OpenCL 3.0 )  
* 21.23.20043: OK  ( OpenCL 3.0 ) 
* 21.22.19967: OK  ( OpenCL 3.0 )
* 21.21.19914: OK  ( OpenCL 3.0 )
* 21.20.19883: OK  ( OpenCL 3.0 )
* 21.19.19792: OK  ( OpenCL 3.0 )
* 21.18.19737: OK  ( OpenCL 3.0 )
* 21.17.19709: OK  ( OpenCL 3.0 )
* 21.16.19610: OK  ( OpenCL 3.0 )
* 21.15.19533: OK  ( OpenCL 3.0 )
* 21.14.19498: OK  ( OpenCL 3.0 )
* 21.13.19438: OK  ( OpenCL 3.0 )  -- From this update, we get correct `LookupBuffer` for SPIRV kernels (experimental)
* 21.12.19358: OK  ( OpenCL 3.0 )
* 21.11.19310: OK  ( OpenCL 3.0 )
* 21.10.19208: OK  ( OpenCL 3.0 )
* 21.09.19150: OK  ( OpenCL 3.0 )
* 21.08.19096: OK  ( OpenCL 3.0 )
* 21.07.19042: OK  ( OpenCL 3.0 )
* 21.06.18993: OK  ( OpenCL 3.0 )
* 21.05.18936: OK  ( OpenCL 3.0 )
* 21.04.18912: OK  ( OpenCL 3.0 )
* 21.03.18857: OK  ( OpenCL 3.0 )
* 21.02.18820: OK  ( OpenCL 3.0 )
* 21.01.18793: OK  ( OpenCL 3.0 )
* 20.47.18513: OK
* 20.46.18421: OK
* 20.45.18403: OK
* 20.41.18123: OK
* 20.37.17906: OK
* 20.28.17293: OK
* 20.27.17231: OK
* 20.26.17199: OK
* 20.25.17111: OK
* 20.24.17065: OK
* 20.22.16952: OK
* 20.21.16886: OK
* 20.20.16837: OK
* 20.19.16754: OK
* 20.18.16699: OK
* 20.17.16650: OK
* 20.16.16582: OK
* 20.15.16524: OK
* 20.14.16441: OK
* 20.13.16352: OK
* 20.12.16259: OK
* 20.11.16158: OK
* 20.10.16087: OK
* 20.09.15980: OK
* 20.08.15750: OK
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

The following drivers have been tested on Linux - Ubuntu 20.04

* 20.28.17293: OK
* 20.22.16952: OK
* 20.16.16582: OK
* 20.13.16352: OK

##### ARM Mali GPUs

[Link](https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel)

* `v1.r9p0-01rel0`: OK

##### Drivers for Intel FPGAs

[Link](http://fpgasoftware.intel.com/17.1/?edition=lite)

* 17.1: OK

##### Intel CPU

[Link](https://software.intel.com/en-us/articles/opencl-drivers)

* 2020.10.4.0.15 : OK
* 18.1.0.0920    : OK 
* 1.2.0.37       : OK (Old version for CentOS)

### NVIDIA

##### NVIDIA GPUs

[Link](https://www.nvidia.com/Download/index.aspx?lang=en-us)

#### OpenCL drivers

* 460.84   : OK
* 460.80   : OK
* 460.73.01: OK
* 460.67   : OK
* 460.56   : OK
* 460.39   : OK
* 450.80.02: OK
* 450.66   : OK
* 450.57   : OK
* 440.100  : OK
* 440.82   : OK
* 440.64   : OK
* 440.59   : OK
* 440.40   : OK
* 440.36   : OK
* 435.21   : OK

#### PTX (CUDA) drivers

* 460.84   : OK
* 460.80   : OK
* 460.73.01: OK
* 460.67   : OK
* 460.56   : OK
* 460.39   : OK
* 450.80.02: OK
* 450.36   : OK
* 440.100  : OK
* 418.56   : OK

### AMD

[Link](https://www.amd.com/en/support)

* 2766.4 (PAL,HSAIL) OK

### Xilinx

[Link](https://www.xilinx.com/products/design-tools/software-zone/sdaccel.html)

* OpenCL 1.0, `xocc` v2018.2:  OK
* OpenCL 1.0, `xocc` v2018.3:  OK
* OpenCL 1.0, `v++`  v2020.2:  OK

## Known Driver Issues

* Intel CPU OpenCL driver `18.1.0.0920` is not working with Ubuntu >= 19.04
* Intel CPU OpenCL driver `1.1` on Mac OSx Catalina (v10.15.7) requires `null` for the local work group size. Any other value (e.g. 256) throws an error with the `CL_INVALID_WORK_GROUP_SIZE` OpenCL error flag, thereby resulting in 25 failed unit-tests.
