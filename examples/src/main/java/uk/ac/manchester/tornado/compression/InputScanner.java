package uk.ac.manchester.tornado.compression;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

public class InputScanner {

    public static ArrayList<Integer> getNumbers(String fileName) throws IOException {
        ArrayList<Integer> list = new ArrayList<>();
        Scanner scanner = null;
        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));
            scanner.useLocale(Locale.US);
            scanner.useDelimiter("[,\\n]");

            while (scanner.hasNext()) {
                if (scanner.hasNextFloat()) {
                    float n = scanner.nextFloat();
                    list.add((int) n);
                } else {
                    scanner.next();
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return list;
    }

    public static void main(String[] args) throws IOException {
        getNumbers("/tmp/numbers.txt");
    }
}
