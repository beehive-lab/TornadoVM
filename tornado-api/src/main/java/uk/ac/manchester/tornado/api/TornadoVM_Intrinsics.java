package uk.ac.manchester.tornado.api;

public class TornadoVM_Intrinsics {

    /**
     * Compute (old + value) and store result at location pointed by p. The function
     * returns old.
     * 
     * @param array
     * @param index
     * @param value
     * @return old value
     */
    public synchronized static int atomic_add(int[] array, int index, int value) {
        int old = array[index];
        array[index] = array[index] + value;
        return old;
    }
}
