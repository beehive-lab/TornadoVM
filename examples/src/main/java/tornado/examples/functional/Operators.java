package tornado.examples.functional;

import java.util.function.Function;
import tornado.api.Parallel;

public class Operators {

    public static <T1, T2> void map(Function<T1, T2> task, T1[] input, T2[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = task.apply(input[i]);
        }
    }

}
