#  FPGA Execution with TornadoVM

**This is a testing version**

**Prerequsites** 

* The ICD loaders for all other OpenCL devices must be disabled:

    Check by run ```$ clinfo ``` and ensure that the only device  available is the Nallatech FPGA, so the output looks similar to:
```       
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

* The working branch should be  ``` feature/fpgaintergation ```.
* Access to the ``` Tornado  ``` machine and permissions to the **/hdd/pre-compilied** directory.

## List of Benchmarks and binary location:
    1.Saxpy 
         Path: /hdd/pre-compilied/pre-tornado/combined/WorkingExamples/Saxpy/lookupBufferAddress
    2.MonteCarlo
         Path: /hdd/pre-compilied/pre-tornado/combined/WorkingExamples/MonteCarlo/lookupBufferAddress
    3.Sgemm 
         Path: /hdd/pre-compilied/pre-tornado/combined/WorkingExamples/Sgemm/lookupBufferAddress
    4.NBody
         Path: /hdd/pre-compilied/pre-tornado/combined/WorkingExamples/Nbody/lookupBufferAddress
        
## How To Execute:
Two additional  parameters are needed for executing the pre-compilied binaries onto FPGAs:

* ``` tornado.opencl.codecache.loadbin=True ```  which enables loading the pre-compilied binaries to TornadoVM for execution 
* ``` tornado.precompilied.dir= ```  which points to the directory containing the pre-compilied binary

In order to run the Saxpy example:

```
    $  tornado -Dtornado.opencl.codecache.loadbin=True -Dtornado.precompilied.dir=/hdd/pre-compilied/pre-tornado/combined/WorkingExamples/Saxpy/lookupBufferAddress uk.ac.manchester.tornado.examples.Saxpy

```

The output should look like similar to the following:
```
 Reprogramming device [0] with handle 1
[0] p385a_sch_ax115 : nalla_pcie (aclnalla_pcie0)

** WARNING: [aclnalla_pcie0] NOT using DMA to transfer 40960 bytes from host to device because of lack of alignment
**                 host ptr (0x7f85b6aa3488) and/or dev offset (0x12080) is not aligned to 64 bytes
[0] p385a_sch_ax115 : nalla_pcie (aclnalla_pcie0)

Reprogramming device [0] with handle 2
** WARNING: [aclnalla_pcie0] NOT using DMA to transfer 40960 bytes from device to host because of lack of alignment
**                 host ptr (0x7f85b6aad4a0) and/or dev offset (0x1c100) is not aligned to 64 bytes

```

