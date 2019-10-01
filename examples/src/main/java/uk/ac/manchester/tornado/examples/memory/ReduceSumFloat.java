package uk.ac.manchester.tornado.examples.memory;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

public class ReduceSumFloat {
    // private static int size = 65536;

    private static void reductionAddFloats(int[] input, @Reduce int[] result) {
        // result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        int[] input = new int[size];
        int[] result = new int[256];
        final int neutral = 0;
        Arrays.fill(result, neutral);

        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> {
            input[i] = r.nextInt(1000);
        });

        //@formatter:off
            TaskSchedule task = new TaskSchedule("s0")
                    .streamIn(input)
                    .task("t0", ReduceSumFloat::reductionAddFloats, input, result)
                    .streamOut(result);
            //@formatter:on

        for (int x = 0; x < 6; x++) {
            task.execute();

            int[] sequential = new int[1];
            reductionAddFloats(input, sequential);

            // Check result

            float diff = Math.abs(sequential[0]) - Math.abs(result[0]);

            // for (int y = 1; y < result.length; y++) {
            // result[0] += result[y];
            // }

            int tempStore = 0;
            for (int idx = 0; idx < 256; idx++) {
                tempStore += input[idx];
            }

            // System.out.println("First: " + tempStore);
            // System.out.println(Arrays.toString(result));
            // System.out.println("Last: " + tempStore2);

            System.out.println("Diff value: seq " + +sequential[0]);
            System.out.println("Diff value: res " + result[0]);
        }

    }
}
