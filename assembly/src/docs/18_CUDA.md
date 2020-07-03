## Running TornadoVM using the PTX backend

#### Prerequisites
In order to run the PTX backend of Tornado, you will need a CUDA compatible device.

#### Installation 
As a first step, TornadoVM must be installed as described [here](1_INSTALL.md).

Then, you will need to setup the `CUDA Toolkit`. If you don't have it installed already, you can follow [this guide](https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html) 

In order to check that the installation has been successful, you can run the following commands:
`nvidia-smi` and `nvcc --version`

The output of `nvidia-smi` should be similar to:
```
+-----------------------------------------------------------------------------+
 | NVIDIA-SMI 440.100      Driver Version: 440.100      CUDA Version: 10.2     |
 |-------------------------------+----------------------+----------------------+
 | GPU  Name        Persistence-M| Bus-Id        Disp.A | Volatile Uncorr. ECC |
 | Fan  Temp  Perf  Pwr:Usage/Cap|         Memory-Usage | GPU-Util  Compute M. |
 |===============================+======================+======================|
 |   0  GeForce GTX 1650    Off  | 00000000:01:00.0 Off |                  N/A |
 | N/A   51C    P8     1W /  N/A |     73MiB /  3914MiB |      0%      Default |
 +-------------------------------+----------------------+----------------------+
                                                                                
 +-----------------------------------------------------------------------------+
 | Processes:                                                       GPU Memory |
 |  GPU       PID   Type   Process name                             Usage      |
 |=============================================================================|
 |    0      1095      G   /usr/lib/xorg/Xorg                            36MiB |
 |    0      1707      G   /usr/lib/xorg/Xorg                            36MiB |
 +-----------------------------------------------------------------------------+
 ```

The output of `nvcc --version` should be similar to:
```
nvcc: NVIDIA (R) Cuda compiler driver
Copyright (c) 2005-2019 NVIDIA Corporation
Built on Wed_Oct_23_19:24:38_PDT_2019
Cuda compilation tools, release 10.2, V10.2.89
```

Next, build TornadoVM and run `tornado --devices`. The output should look like this:
```
   Number of Tornado drivers: 2
   Total number of devices  : 1
   Tornado device=0:0
   	CUDA-PTX -- GeForce GTX 1650
   		Global Memory Size: 3.8 GB
   		Local Memory Size: 48.0 KB
   		Workgroup Dimensions: 3
   		Max WorkGroup Configuration: [1024, 1024, 64]
   		Device OpenCL C version: N/A
   
   Total number of devices  : 2
   Tornado device=1:0
   	NVIDIA CUDA -- GeForce GTX 1650
   		Global Memory Size: 3.8 GB
   		Local Memory Size: 48.0 KB
   		Workgroup Dimensions: 3
   		Max WorkGroup Configuration: [1024, 1024, 64]
   		Device OpenCL C version: OpenCL C 1.2
   
   Tornado device=1:1
   	Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
   		Global Memory Size: 24.8 GB
   		Local Memory Size: 64.0 KB
   		Workgroup Dimensions: 3
   		Max WorkGroup Configuration: [256, 256, 256]
   		Device OpenCL C version: OpenCL C 2.0
```
Where the first driver will always be the CUDA device detected by the PTX backend.

#### Possible issues
After the installation of the `CUDA Toolkit`, please make sure that you follow the [environment setup](https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html#environment-setup) to include the required environment variables.

In some cases the driver module might not get loaded due to a [blacklist file](https://forums.developer.nvidia.com/t/nvidia-driver-is-not-loaded-ubuntu-18-10/70495/2).
You can remove this by running:
`sudo rm /etc/modprobe.d/blacklist-nvidia.conf`  
  
The driver can also fail to load if it is not selected in `prime-select`. In order to select it, you can run `prime-select nvidia` or `prime-select on-demand`.

For older versions of the driver, you might have to point your `LIBRARY_PATH` variable to the `libcuda` library in order to build TornadoVM.  
Example: `export LIBRARY_PATH=$LIBRARY_PATH:/usr/local/cuda/lib64/stubs`
  
After these changes, a reboot might be required for the driver module to be loaded.


#### Testing

We have tested the PTX backend of TornadoVM on the following configurations:

|GPU   |Arch   |Version   |Target   |Driver version   |CUDA version   |Status   |
|---    |---    |---    |---    |---    |---    |---    |
|Quadro GP100   |Pascal   |6.0   |sm_60   |384.111   |9.0   |Functional|
|GeForce GTX 1650   |Turing   |6.5   |sm_75   |440.100   |10.2   |OK|
|GeForce 930MX   |Maxwell   |6.4   |sm_50   |418.56   |10.1   |OK|
|GeForce 930MX   |Maxwell   |6.5   |sm_50   |450.36   |11.0   |OK|

Note that `Functional` denotes that there might be some issues with the backend-driver interaction.   
Specifically, running TornadoVM on the `Quadro GP100` with the PTX backend might sometimes cause segmentation faults.   

