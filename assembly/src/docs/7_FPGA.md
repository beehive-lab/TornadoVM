# Tornado FPGA Support #

Tornado supports execution and prototyping with OpenCL compatible Intel and Xilinx FPGAs. For debugging you can use common IDEs from Java ecosystem. 

**IMPORTANT NOTE:** The minimum input size to run on the FPGA is 64 elements (which correspondonds internally with the local work size in OpenCL). 

### Pre-requisites

We have currently tested with an Intel Nallatech-A385 FPGA (Intel Arria 10 GT1150) and a Xilinx KCU1500 FPGA card.
We have also tested it on the AWS EC2 F1 instance with xilinx_aws-vu9p-f1-04261818_dynamic_5_0 device.

* HLS Versions: Intel Quartus 17.1.0 Build 240, Xilinx SDAccel 2018.2, Xilinx SDAccel 2018.3
* TornadoVM Version: > 0.4
* AWS AMI Version: 1.6.0

If the OpenCL ICD loaders are installed correclty, the output of the ```clinfo``` it should be the following:  
```bash
$ clinfo
   Number of platforms                               1
   Platform Name                                   Intel(R) FPGA SDK for OpenCL(TM)
   Platform Vendor                                 Intel(R) Corporation
   Platform Version                                OpenCL 1.0 Intel(R) FPGA SDK for OpenCL(TM), Version 17.1
   Platform Profile                                EMBEDDED_PROFILE
   Platform Extensions                             cl_khr_byte_addressable_store cles_khr_int64 cl_intelfpga_live_object_tracking cl_intelfpga_compiler_mode cl_khr_icd cl_khr_3d_image_writes
   Platform Extensions function suffix             IntelFPGA
 
   Platform Name                                   Intel(R) FPGA SDK for OpenCL(TM)
   Number of devices                                 1
   Device Name                                     p385a_sch_ax115 : nalla_pcie (aclnalla_pcie0)
   Device Vendor                                   Nallatech ltd
   Device Vendor ID                                0x1172
   Device Version                                  OpenCL 1.0 Intel(R) FPGA SDK for OpenCL(TM), Version 17.1
   Driver Version                                  17.1
   Device OpenCL C Version                         OpenCL C 1.0
   Device Type                                     Accelerator
```
## Step 1: Update the "etc/vendor_fpga.conf" file with the necessary information (i.e. fpga plarform name (DEVICE_NAME), HLS compiler flags (FLAGS), HLS directory (DIRECTORY_BITSTREAM).
TornadoVM will automatically load the user-defined configurations according to the vendor of the underlying FPGA device. 
You can also run TornadoVM with your configuration file, by using the `-Dtornado.fpga.conf.file=FILE` flag. 

### Example of configuration file for Intel Nallatech-A385 FPGA (Intel Arria 10 GT1150): 
```$ vim etc/intel-fpga.conf```

```
[device]
DEVICE_NAME=p385a_sch_ax115
[flags]
FLAGS=-v -fast-compile -high-effort -fp-relaxed -report -incremental -profile
DIRECTORY_BITSTREAM=fpga-source-comp/
```

### Example of configuration file for Xilinx KCU1500: 
```$ vim etc/xilinx-fpga.conf```

```
[device]
DEVICE_NAME=xilinx_kcu1500_dynamic_5_0
[flags]
FLAGS=-O3 -j12
DIRECTORY_BITSTREAM=fpga-source-comp/
```

### Example of configuration file for AWS xilinx_aws-vu9p-f1-04261818_dynamic_5_0: 
```$ vim etc/xilinx-fpga.conf```

```
DEVICE_NAME=/home/centos/src/project_data/aws-fpga/SDAccel/aws_platform/xilinx_aws-vu9p-f1-04261818_dynamic_5_0/xilinx_aws-vu9p-f1-04261818_dynamic_5_0.xpfm
[flags]
FLAGS=-O3 -j12
DIRECTORY_BITSTREAM=fpga-source-comp/
```

## Step 2: Select one of the three FPGA Execution Modes  

### Full JIT Mode  

This mode allows the compilation and execution of a given task for the FPGA. As it provides full end-to-end execution, the compilation is expected to take up to 2 hours due HLS bistream generation process.  

The generated FPGA bitstream as well as the the generated OpenCL code can be found in the `fpga-source-comp/` directory which is place in the local directory of execution. 

Example:

```bash
tornado \
    -Ds0.t0.device=0:1 \
    -Dtornado.opencl.accelerator.fpga=true \
    -Dtornado.opencl.userelative=True \
    uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 1024 normal 1
```

### Ahead of Time Execution Mode

Ahead of time execution mode allows the user to generate a pre-generated bitstream of the Tornado tasks and then load it in a separated execution. The FPGA bitstream file should be named as `lookupBufferAddress`. 

Example:  

```bash
tornado \
    -Ds0.t0.device=0:1 \
    -Ds0.t0.global.dims=1024 \
    -Ds0.t0.local.dims=64 \
    -Dtornado.precompiled.binary=/path/to/lookupBufferAddress,s0.t0.device=0:1 \
    -Dtornado.opencl.userelative=True \
    uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 1024 normal 10
```

### Emulation Mode [on Intel FPGAs]

Emulation mode can be used for fast-prototying and ensuring program functional correctness before going through the full JIT process (HLS).

Before executing the tornado program, the following env variable needs to be exported:  

```bash
$ export CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1
```

Example:  

```bash
env CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1 tornado \
    -Ds0.t0.device=0:1 \
    -Dtornado.opencl.userelative=True \
    uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 1024 normal 10
```

