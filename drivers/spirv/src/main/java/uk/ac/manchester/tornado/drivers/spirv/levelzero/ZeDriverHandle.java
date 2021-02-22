package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeDriverHandle extends ZePointer {

    private int numDrivers;
    long[] ze_driver_handle_t_ptr;

    public ZeDriverHandle(int numDrivers) {
        this.numDrivers = numDrivers;
        this.ze_driver_handle_t_ptr = new long[numDrivers];
    }

    public int getNumDrivers() {
        return numDrivers;
    }

    public long[] getZe_driver_handle_t_ptr() {
        return this.ze_driver_handle_t_ptr;
    }

    public void setZe_driver_handle_t_ptr(long[] pointers) {
        this.ze_driver_handle_t_ptr = pointers;
    }

}
