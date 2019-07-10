# Tornado FPGA Support #

Tornado supports execution and prototyping with OpenCL compatible Intel/Altera FPGAs. For debuging you can use common IDEs from Java ecosystem. 

**IMPORTANT NOTE:** The minimum input size to run on the FPGA is 64 elements (which correspondonds internally with the local work size in OpenCL). 

### Pre-requisites

We have currently tested with a Nallatech-A385 FPGA (Intel Arria 10 GT1150).

* Quartus Version: 17.1.0 Build 240
* Tornado Version: 0.2

If the OpenCL ICD loaders are installed correclty, the output of the ```clinfo``` it shoudl be the following:  
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

## Execution Modes  

### Full JIT Mode  

This mode allows the compilation and execution of a given task for the FPGA. As it provides full end-to-end execution, the compilation is expected to take up to 2 hours due HLS bistream generation process.  

The generated FPGA bitstream as well as the the generated OpenCL code can be found in the `fpga-source-comp/` directory which is place in the local directory of execution. 

Example:

```bash 
$ tornado \
    -Ds0.t0.device=0:1 \
    -Dtornado.assembler.removeloops=true \
    -Dtornado.opencl.accelerator.fpga=true \
    -Dtornado.fpga.flags=v,report      \
    -Dtornado.opencl.userelative=True  \
    uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 256 normal 1  
```

### Ahead of Time Execution Mode

Ahead of time execution mode allows the user to generate a pre-generated bitstream of the Tornado tasks and then load it in a separated execution. The FPGA bitstream file should be named as `lookupBufferAddress`. 

Example:  

```bash
$ tornado \
    -Ds0.t0.device=0:1 \
    -Dtornado.precompiled.binary=/path/to/lookupBufferAddress,s0.t0.device=0:1 \
    -Dtornado.opencl.userelative=True  \
    -Ds0.t0.global.dims=1024 \
    -Ds0.t0.local.dims=16    \
    uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 1024 normal 1 
```

### Emulation Mode [Intel/Altera Tools]

Emulation mode can be used for fast-prototying and ensuring program functional correctness before going through the full JIT process (HLS).

The following two steps are required:

1) Before executing the tornado program, the following env variable needs to be exported:  

```bash
$ export CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1
```

2) All the runtime flags are the same used during the full JIT mode plus the following:  

```bash
-Dtornado.fpga.emulation=true
```

Example:  

```bash
$ env CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1 tornado  \ 
        -Ds0.t0.device=0:1  \
        -Dtornado.assembler.removeloops=true  \
        -Dtornado.opencl.accelerator.fpga=true \
        -Dtornado.opencl.userelative=True  \
        -Dtornado.fpga.emulation=true  \
        -Dtornado.fpga.flags=v,report  \
        uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 1024 normal 1 
```
