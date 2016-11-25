package tornado.drivers.opencl.graal.meta;

import java.util.Arrays;
import tornado.common.Tornado;

public class Coarseness {

    private final int[] values;

    public Coarseness(int depth) {
        values = new int[depth];

        String str[] = Tornado.getProperty("tornado.coarseness", "1,1,1").split(",");
        for (int i = 0; i < values.length; i++) {
            values[i] = Integer.parseInt(str[i]);
        }
    }

    public int getCoarseness(int index) {
        return values[index];
    }

    public void setCoarseness(int index, int value) {
        values[index] = value;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
