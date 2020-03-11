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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

import java.io.IOException;
import java.util.*;

public class BufetTornado {


    //------Const definition segment------
    private static final int genes_population = 25000;          //maximum amount of Genes
    private static final int miRNA_groups = 10000;              //the amount of random miRNAs target groups
    private static final int warming_up_iterations = 15;
    private static final int chunks = 2;

    //------Methods definition segment------
    // Create a Hash table with unique instances and assign an ID
    public static HashMap<String, Integer> getUniqueInstances(ArrayList<String> inputData) {

        HashMap<String, Integer> map = new HashMap<>();
        int cnt = 0;                                            //First ID is the ZERO

        for (String inputDatum : inputData) {
            if (!map.containsKey(inputDatum)) {
                map.put(inputDatum, cnt++);                     //Associate key with unique ID value
            }
        }
        return map;
    }


    //Read the differentially expressed miRNAs provided by the user
    public static int[] getMiRNAsPhaseA(String filename, String delimiter, HashMap<String, byte[]> map, HashMap<String, ArrayList<Integer>> goCatGenesUnq) throws IOException {

        ArrayList<String> list = new ArrayList<>(InputScanner.getString(filename, delimiter));
        byte[] array = new byte[genes_population];
        int [] retArray = new int[(2 + goCatGenesUnq.size())]; //temporary store of return values
        int target_genes = 0;                                  //Count ones in array (gene_map)
        int group_found = 0;                                   //miRNA matches between the user-defined miRNA sequence and the Database of miRNAs
        int idx = 2;                                           //Avoid the first two positions for target_genes and group_found

        for (String token : list) {
            if (map.containsKey(token)) {
                group_found++;
                for (int i = 0; i < genes_population; i++) {
                    array[i] |= map.get(token)[i];
                }
            }
        }
        retArray[0] = group_found; //Position 0: First return value

        for (int i = 0; i < genes_population; i++) {
            if (array[i] == 1) {
                target_genes++;
            }
        }
        retArray[1] = target_genes; //Position 1: Second return value

        for (HashMap.Entry<String,ArrayList<Integer>> entry: goCatGenesUnq.entrySet()) {
            //how many Genes are associated to the specific goCategory
            int intersection = 0;
            for (Integer token : entry.getValue()) {
                if (array[token] == 1) {
                    intersection++;
                }
            }
            retArray[idx] = intersection;
            idx++;
        }
        return retArray;
    }


    //Read the differentially expressed miRNAs provided by the user
    //Generate the checkGO and nocheckGO data structures
    public static HashMap<String, ArrayList<String>> getMiRNAsPhaseB(HashMap<String, ArrayList<Integer>> goCatGenesUnq, HashMap<String,
            ArrayList<String>> goCategories, int[] phaseAarr, boolean goOrNogo) {

        HashMap<String, ArrayList<String>> map = new HashMap<>();
        int idx = 2;

        for (HashMap.Entry<String,ArrayList<Integer>> entry: goCatGenesUnq.entrySet()) {
            if ((phaseAarr[idx] > 0) && (goOrNogo == true)) {                           //Generate checkGO
                map.put(entry.getKey(), new ArrayList<String>());
                map.get(entry.getKey()).add(entry.getKey());                            //goCategory
                map.get(entry.getKey()).add(Integer.toString(entry.getValue().size())); //Amount of Genes for the goCategory
                map.get(entry.getKey()).add(Integer.toString(phaseAarr[idx]));          //intersection
                map.get(entry.getKey()).add(Double.toString((double)phaseAarr[idx]/(double) phaseAarr[1])); //overall_proportion
                map.get(entry.getKey()).add(Double.toString(0.0));                   //mean_overlap
                for (String token:goCategories.get(entry.getKey())) {
                    map.get(entry.getKey()).add(token);
                }
            }

            if ((phaseAarr[idx] <= 0) && (goOrNogo == false)) {                         //Generate nocheckGO
                map.put(entry.getKey(), new ArrayList<String>());
                map.get(entry.getKey()).add(entry.getKey());                            //goCategory
                map.get(entry.getKey()).add(Integer.toString(entry.getValue().size())); //Amount of Genes for the goCategory
                map.get(entry.getKey()).add(Integer.toString(0));                    //intersection
                map.get(entry.getKey()).add(Double.toString(0.0));                   //overall_proportion
                for (String token:goCategories.get(entry.getKey())) {
                    map.get(entry.getKey()).add(token);
                }
            }
            idx++;
        }
        return map;
    }


    //Generate a random index
    public static int[] generateRandom(int upperBound, int size) {

        int[] array = new int[size];

        Random rand = new Random();                 //To @Parallel, remove Java random class call
        for (int i = 0; i < size ; i++)
            array[i] = rand.nextInt(upperBound);
        return array;
    }


    //Generate the miRNAs target groups
    //@Params: Input: randID, gene_population, size, miRNA_groups, onlyGeneVector, genePopBitLength
    //         Output: map_all
    public static void getRandomTargetGroup(int[] randID, int size, byte[] onlyGeneVector, int genePopBitLength,
                                            int offset, int end, final int genes_population_l, int[] array) {

        for ( int k = 0; k < end; k++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < genePopBitLength; j++) {
                    for (int shiftAmount = 0; shiftAmount < 16; shiftAmount++) {
                        int idxA = ((randID[(((k * size) + i) + offset)] * genes_population_l) + ((j * 16) + shiftAmount));
                        array[((k * genePopBitLength) + j)] |= (onlyGeneVector[idxA] << shiftAmount);
                    }
                }
            }
        }
    }


    //Calculate the number of genes targeted by each random miRNA group
    //@Params: Input: miRNA_groups_l, genePopBitLength, map_all
    //         Output: countOnes
    public static void calculateCounts(final int miRNA_groups_l, int genePopBitLength, int[] map_all, short[] countOnes, short[] countOnes_local) {

        for (@Parallel int i = 0; i < miRNA_groups_l; i++) {
            countOnes_local[0] = 0;
            for (int j = 0; j < genePopBitLength; j++) {
                for (int shiftAmount = 0; shiftAmount < 16; shiftAmount++) {
                    int idx = ((i * genePopBitLength) + ((j * 16) + shiftAmount));
                    //countOnes_local[0] += (short)((map_all[idx] >> shiftAmount) & 1);
                    countOnes[i] += (short)((map_all[idx] >> shiftAmount) & 1);
                }
            }
            //countOnes[i] = countOnes_local[0];
        }
    }

    //Calculate the number of genes targeted by each random miRNA group
    public static short[] calculateCounts2(int sizeA, int[] map_all) {

        short[] countOnes = new short[sizeA];
        short value = 0;
        int genePopBitLength = (genes_population / 16);

        for (int i = 0; i < sizeA; i++) {
            value = 0;
            for (int j = 0; j < genePopBitLength; j++) {
                for (int shiftAmount = 0; shiftAmount < 16; shiftAmount++) {
                    value = (short)(value + ((map_all[((i * genePopBitLength) + j)] >> shiftAmount) & 1));
                }
            }
            countOnes[i] = value;
        }
        return countOnes;
    }


    //Calculate the intersections of all random miRNA sets
    // for the candidate GO category and calculate the sets with greater overlap
    //@Params: Input: size, miRNA_group, genePopBitLength, map_all, geneIDperGoCat, geneIDsArray, countOnes, tempMeanOverlap, overallProportion
    //         Output: pvalues, tempMeanOverlap
    public static void findIntersections(int size, int miRNA_groups_l, int genePopBitLength, int[] map_all, int[] geneIDperGoCat, int[] geneIDsArray,
                                     short[] countOnes, double[] tempMeanOverlap, double[] overallProportion, long[] pvalues) {

        for ( int k = 0; k < size; k++) {
            for (int i = 0; i < miRNA_groups_l; i++) {
                int intersection = 0;
                for (int j = 0; j < geneIDperGoCat[k]; j++) {
                    intersection += ((map_all[((i * genePopBitLength) + (geneIDsArray[j] >> 4))] >> (geneIDsArray[j] & 15)) & 1);
                }
                double overlap = (double)intersection / (double)countOnes[i];
                tempMeanOverlap[k] += overlap;
                if (overlap >= overallProportion[k]) {
                    pvalues[k]++;
                }
            }
        }
    }


    //Calculate the intersections of all random miRNA sets
    // for the candidate GO category and calculate the sets with greater overlap
    public static long[] findIntersections2(int size, int[] map_all, short[] countOnes,
                                           HashMap<String, ArrayList<String>> checkGO, HashMap<String, ArrayList<Integer>> goCatUniqueGenes) {

        int idx = 0;
        long[] array = new long[size];
        int genePopBitLength = (genes_population / 16);

        for (HashMap.Entry<String,ArrayList<String>> entry: checkGO.entrySet()) {
            double tempMeanOverlap = Double.parseDouble(entry.getValue().get(4));           //temp var for mean_overlap
            double overallProportion = (Double.parseDouble(entry.getValue().get(3)));

            for (int i = 0; i < miRNA_groups; i++) {
                int intersection = 0;
                //System.out.println("New Intersection");
                for(Integer token: goCatUniqueGenes.get(entry.getKey())) {
                    //System.out.println("Go: "+  entry.getKey() + " GeneID: " + token);
                    // Convert division token/8 to token >> 3, Convert module token % 8 to (token & (8 - 1))
                    //System.out.println("map_all: " + map_all[((i * genePopBitLength) + (token >> 4))]);
                   // System.out.println("token & 15: " + (token & 15));
                   // System.out.println(("Final value: " + (map_all[((i * genePopBitLength) + (token >> 4))] >> (token & 15))));
                    intersection += ((map_all[((i * genePopBitLength) + (token >> 4))] >> (token & 15)) & 1);
                   // System.out.println("Intersection: " + intersection);
                }
               // System.out.println("Final intersection: " + intersection);

                double overlap = (double)intersection / (double)countOnes[i];
               // System.out.println("Overall: " + overlap);
                tempMeanOverlap += overlap;
                if ((overlap >= overallProportion)) {                                     //overall_proportion
                    array[idx]++;                                                         //pvalue
                }
            }
            entry.getValue().set(4,Double.toString(tempMeanOverlap));
            idx++;
        }
        return array;
    }


    //Write to file the output of the miRNA enrichment process
    public static void WriteOutput(String fileName, ArrayList<Double> pvalueList, ArrayList<String> fdrList,
                                   HashMap<String, ArrayList<String>> checkGO,  HashMap<String, ArrayList<String>> nocheckGO) {

        ArrayList<String> text = new ArrayList<>(); //text to write to the file
        int i = 0, j = 0;
        double mean_overlap = 0.0;

        text.add("GO-term-ID  GO-term-size  Gene-Overlap ");
        text.add("Mean-Target-Overlap-Proportion  empirical-p-value  Benjamini-Hochberg-0.05-FDR \n");
        text.add("\n");

        for (HashMap.Entry<String,ArrayList<String>> entry: checkGO.entrySet()) {
            text.add(entry.getValue().get(0));                                                      //go Category
            text.add("\t");
            text.add(entry.getValue().get(5));                                                      //name
            text.add("\t");
            text.add(entry.getValue().get(1));                                                      //size
            text.add("\t");
            text.add(entry.getValue().get(3));                                                      //overall_proportion
            text.add("\t");
            mean_overlap =  (Double.parseDouble(entry.getValue().get(4)) / (double)miRNA_groups);   //mean_overlap
            text.add(Double.toString(mean_overlap));
            text.add("\t");
            text.add(Double.toString(pvalueList.get(i)));                                           //pvalue
            text.add("\t");
            text.add(fdrList.get(i));
            text.add("\n");
            i++;
        }
        for (HashMap.Entry<String,ArrayList<String>> entry: nocheckGO.entrySet()) {
            text.add(entry.getValue().get(0));
            text.add("\t");
            text.add(entry.getValue().get(4));
            text.add("\t");
            text.add(entry.getValue().get(1));
            text.add("\t");
            text.add(entry.getValue().get(3));
            text.add("\t");
            text.add("0.0");
            text.add("\t");
            text.add(fdrList.get(i + j));
            text.add("\n");
            j++;
        }
        InputScanner.writeString(fileName,text);
    }


    public static void main(String[] args) throws IOException {

        //Consistency check
        if (args.length < 5) {
            //Command-line arguments sequence:
            //miRanda_dataset.csv, delimiter, annotation_dataset.csv, delimiter, miRNA_sequence, output_file
            System.out.println("Error: Argument(s) is(are) missing (" + args.length + " out of 5)!");
        }

        //System.out.println("Reading " + args[0] + " and generate a Hashmap for unique miRNAs.");
        //HashMap<String, Integer> miRNAs = new HashMap<>(getUniqueInstances(InputScanner.getString(args[0], args[1])));

        //Store interactions between miRNAs and Genes
        System.out.println("Reading " + args[0] + " and generate a Hashmap for miRNA-Gene interactions.");
        HashMap<String, ArrayList<String>> miRNA_Genes = new HashMap<>(InputScanner.readString(args[0], args[1], 0, 1, false));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for unique Genes.");
        HashMap<String, Integer> genes = new HashMap<>(getUniqueInstances(InputScanner.getString(args[2], args[3])));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for Go categories.");
        //Store only the first instance of the go Category name
        HashMap<String, ArrayList<String>> goCategories = new HashMap<>(InputScanner.readString(args[2], args[1], 1, 2, true));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for GoGenes <goCategory, Gene, ...>.");
        HashMap<String, ArrayList<String>> goCatGenes = new HashMap<>(InputScanner.readString(args[2], args[1], 1, 0, true));

        //Each go Category contains a vector of Gene IDs
        HashMap<String, ArrayList<Integer>> goCatUniqueGenes = new HashMap<>();
        for (HashMap.Entry<String, ArrayList<String>> entry : goCatGenes.entrySet()) {
            goCatUniqueGenes.put(entry.getKey(), new ArrayList<Integer>());
            for (String token : entry.getValue()) {
                if (genes.containsKey(token)) {
                    goCatUniqueGenes.get(entry.getKey()).add(genes.get(token));
                }
            }
        }

        //Each miRNA is associated with a bit vector of active Genes
        HashMap<String, byte[]> interactions = new HashMap<>();
        for (HashMap.Entry<String, ArrayList<String>> entry : miRNA_Genes.entrySet()) {
            interactions.put(entry.getKey(), new byte[genes_population]);
            for (String token : entry.getValue()) {
                if (!genes.containsKey(token)) {
                    //Append the new gene to Genes' structure
                    int maxId = 0;
                    for (Map.Entry Entry : genes.entrySet()) {
                        int value = (int) Entry.getValue();
                        if (value > maxId) {
                            maxId = value;
                        }
                    }
                    genes.put(token, (maxId + 1));
                }
                (interactions.get(entry.getKey()))[genes.get(token)] = 1;
            }
        }

        //Cast Hash table to an array of byte values
        byte[] onlyGeneVector = new byte[genes_population * interactions.size()];
        int j = 0;                                                          //aux index to parse array
        for (HashMap.Entry<String, byte[]> entry : interactions.entrySet()) { //interactions.size() iterations
            int i = 0;
            for (byte token : entry.getValue()) {                            //gene_population iterations
                //A new miRNA target group is stored every genes_population byte values
                onlyGeneVector[(j * genes_population) + i] = token;
                i++;
            }
            j++;
        }

        //Store the miRNA target group for the user-defined miRNA sequence
        System.out.println("Generate miRNA target group for the user-defined miRNA sequence (Phase A, and B).");
        int[] getMiRNAsretVal;
        getMiRNAsretVal = getMiRNAsPhaseA(args[4], args[1], interactions, goCatUniqueGenes);

        HashMap<String, ArrayList<String>> checkGO;
        checkGO = getMiRNAsPhaseB(goCatUniqueGenes, goCategories, getMiRNAsretVal, true);

        HashMap<String, ArrayList<String>> nocheckGO;
        nocheckGO = getMiRNAsPhaseB(goCatUniqueGenes, goCategories, getMiRNAsretVal, false);

        System.out.println("Generate the miRNAs target groups.");
        int miRNA_groups_splitted = miRNA_groups / chunks;
        int genePopBitLength = (int) Math.ceil(genes_population / 16);
        int checkGoSize = checkGO.size();
        int sizeA = getMiRNAsretVal[0] * miRNA_groups;

        int[] randID = generateRandom(miRNA_Genes.size(), sizeA);
        int[] map_all = new int[miRNA_groups * genePopBitLength];
        //short[] countOnes = new short[miRNA_groups];

        int chunkSize = map_all.length / chunks;

        //short[] countOnes_local = new short[1];

        /*ArrayList<Integer> geneIDs = new ArrayList<>();
        int[] geneIDperGoCat = new int[checkGoSize];
        //Arrays.fill(geneIDperGoCat,0);


        double[] tempMeanOverlap = new double[checkGoSize];
        //Arrays.fill(tempMeanOverlap,0.0);
        double[] overallProportion = new double[checkGoSize];
        //Arrays.fill(overallProportion,0.0);
        int idxA = 0;
        for (HashMap.Entry<String,ArrayList<String>> entry: checkGO.entrySet()) {
            tempMeanOverlap[idxA] = Double.parseDouble(entry.getValue().get(4));
            overallProportion[idxA] = (Double.parseDouble(entry.getValue().get(3)));
            geneIDs.addAll(goCatUniqueGenes.get(entry.getKey()));                   //GeneID associated with each goCategory
            geneIDperGoCat[idxA] = goCatUniqueGenes.get(entry.getKey()).size();     //dynamic amount of Genes allocated to each Go category
            idxA++;
        }

        int[] geneIDsArray = geneIDs.stream().mapToInt(Integer::intValue).toArray();    //plain int array to store geneIDs
        //long[] pvalues = new long[checkGoSize];
        //Arrays.fill(pvalues,0);

        */

        System.out.println("miRNA size: " + miRNA_Genes.size() + " sizeA: " + sizeA);
        System.out.println("Size: " + getMiRNAsretVal[0] + " Splitted: " + miRNA_groups_splitted + " genePopBitLength: " + genePopBitLength);



        // Setup TornadoVM
        System.out.println("Configure and execute TornadoVM");
        for (int i = 0; i < chunks; i++) {
            System.out.println("Offset i " + i + " is " + ((i * miRNA_groups * getMiRNAsretVal[0])/chunks));
            int[] array = new int[chunkSize];
            TaskSchedule s0 = new TaskSchedule("x" + i);
            s0.task("t0", BufetTornado::getRandomTargetGroup, randID, getMiRNAsretVal[0], onlyGeneVector, genePopBitLength,
                    ((i * miRNA_groups * getMiRNAsretVal[0])/chunks), miRNA_groups_splitted, genes_population, array);
            s0.streamOut(array);

            //for (int i = 0; i < warming_up_iterations; i++) {
            s0.execute();
            //}
            
            System.arraycopy(array,0, map_all, (i * chunkSize), array.length);
        }

        //System.out.println("Rand: " + Arrays.toString(randID));
       // System.out.println(Arrays.toString(map_all));

        short[] countOnes;
        countOnes = calculateCounts2(miRNA_groups, map_all);

        //System.out.println("Calculate counts: " + Arrays.toString(countOnes));

        long[] pvalues;
        pvalues = findIntersections2(checkGoSize, map_all, countOnes, checkGO, goCatUniqueGenes);


        //s0.task("t1" + i, BufetTornado::calculateCounts,miRNA_groups, genePopBitLength, array, countOnes, countOnes_local);
        //s0.streamOut(countOnes);
        //s0.task("t2" + i, BufetTornado::findIntersections,checkGoSize, miRNA_groups, genePopBitLength, array, geneIDperGoCat,
        //                    geneIDsArray, countOnes, tempMeanOverlap, overallProportion, pvalues);
        // s0.streamOut(pvalues, tempMeanOverlap);


        /*int idxB = 0;
        for (HashMap.Entry<String,ArrayList<String>> entry: checkGO.entrySet()) {
            System.out.println(tempMeanOverlap[idxB]);
            entry.getValue().set(4,Double.toString(tempMeanOverlap[idxB]));       //Update checkGO structure
            idxB++;
        }*/

        System.out.println("Calculate the BH FDR correction.");
        BenjaminHochberg bh = new BenjaminHochberg();
        bh.benjaminHochberg(checkGO, nocheckGO, pvalues, miRNA_groups);

        ArrayList<Double> pvalueList;
        ArrayList<String> fdrList;
        pvalueList = bh.returnPvalueList();
        fdrList = bh.returnFdrList();

        System.out.println("Write output to file " + args[5] + ".");
        WriteOutput(args[5], pvalueList, fdrList, checkGO, nocheckGO);

        System.out.println("Bufet algorithm has finished.");
    }

}
