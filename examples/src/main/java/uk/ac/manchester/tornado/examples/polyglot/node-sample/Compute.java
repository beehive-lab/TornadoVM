import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import java.util.stream.IntStream;
import java.util.*;

public class Compute {

    public static String getString() {
        return "Hello from Java";
    }

    private static void vectorAddFloat(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static float[] compute() {
        final int numElements = 16;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = 8;
            b[i] = 2;
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(a, b)
                .task("t0", Compute::vectorAddFloat, a, b, c)
                .streamOut(c)
                .execute();
        //@formatter:on

        return c;
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(compute()));
    }
}
