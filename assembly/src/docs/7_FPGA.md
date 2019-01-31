# Tornado FPGA Support #


### Pre-requisites

Currently tested with Nalatech a385 FPGA.

* BSP Version
* Quartus Version 
* XXXXXXXX


## Execution Modes 

### Full JIT 

### Ahead of Time Execution Mode

### Emulation Mode [Intel/Altera Tools]

Emulation mode can be used for fast-prototying and ensuring program functional correctness before going through the full JIT process (HLS).

The following two steps are required:

1) Before executing the tornado program, the following env variable needs to be exported:  
           ``` $ export CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1 ```

2) All the runtime flags are the same used during the full JIT mode plus the following:  
           ``` -Dtornado.fpga.emulation=true ```


