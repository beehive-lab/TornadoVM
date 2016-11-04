package tornado.lang;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class Debug {

    /**
     * prints a message from the zeroth thread
     *
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id, 0, 0]
     *
     * @param id selected thread id
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id0, id1, 0]
     *
     * @param id0 selected thread id
     * @param id1 selected thread id
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id0, int id1, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from the selected thread [id0, id1, id2]
     *
     * @param id0 selected thread id
     * @param id1 selected thread id
     * @param id2 selected thread id
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void tprintf(int id0, int id1, int id2, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * conditionally prints a message from any thread where cond evaluatest to
     * true
     *
     * @param cond condition to evaluate
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void printf(boolean cond, String msg, Object... args) {
        shouldNotReachHere();
    }

    /**
     * prints a message from all threads
     * @param msg format string as per OpenCL spec
     * @param args arguments to format
     */
    public static void printf(String msg, Object... args) {
        shouldNotReachHere();
    }

}
