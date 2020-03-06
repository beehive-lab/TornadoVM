/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * *
 */
package uk.ac.manchester.tornado.examples.bufet;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class InputScanner {

    // Read file. A string per line.
    public static ArrayList<String> getString(String fileName, String delimiter) throws IOException {

        ArrayList<String> list = new ArrayList<>();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));
            scanner.useDelimiter(delimiter);

            while (scanner.hasNext()) {
                list.add(scanner.next());
                scanner.nextLine();
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return list;
    }


    //Read file. Specific tokens in a line.
    public static HashMap<String, ArrayList<String>> readString(String fileName, String delimiter, int StrPosA, int StrPosB, boolean UniqueOccur) throws IOException {

        HashMap<String,ArrayList<String>> map = new HashMap<>();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));

            while (scanner.hasNextLine()) {
                String token = scanner.nextLine();
                String key = token.split(delimiter)[StrPosA];
                String value = token.split(delimiter)[StrPosB];

                if (!map.containsKey(key)) {
                    map.put(key, new ArrayList<String>());
                }

                if (UniqueOccur) {                                      //special case for goCategories data structure
                    if (!(map.get(key).contains(value))) {
                        map.get(key).add(value);
                    }
                }
                else {
                    map.get(key).add(value);
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return map;
    }


    //Write to file.
    public static void writeString(String fileName, ArrayList<String> text) {

        try {
            FileWriter writer = new FileWriter(fileName);
            for(String token: text) {
                writer.write(token);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}