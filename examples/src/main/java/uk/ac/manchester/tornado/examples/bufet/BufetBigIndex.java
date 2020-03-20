/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.examples.bufet;

import java.io.IOException;
import java.util.*;

/**
 *
 * Description: Java version-1 of the Boosting the Unbiased Functional
 * Enrichment Analysis (BUFET) algorithm. This version focuses on reducing the
 * memory requirements of the application. Not accelerated through TornadoVM.
 *
 * Konstantinos Zagganas, Thanasis Vergoulis, Ioannis S. Vlachos, Maria D.
 * Paraskevopoulou, Spiros Skiadopoulos and Theodore Dalamagas. BUFET: boosting
 * the unbiased miRNA functional enrichment analysis using bitsets. BMC
 * Bioinformatics volume 18, page 399, doi 10.1186/s12859-017-1812-8, 2017.
 * https://bmcbioinformatics.biomedcentral.com/articles/10.1186/s12859-017-1812-8
 *
 * C++ bufetApp version: git clone https://github.com/diwis/BUFET.git
 * 
 * Command-line arguments format:
 *
 * tornado uk.ac.manchester.tornado.examples.bufet.BufetBigIndex
 * miRanda_dataset.csv "\\n|\|" annotation_dataset.csv "\\n|\|" miRNA-5.txt
 * output_file.txt
 *
 * Download and unzip input data-set:
 * http://carolina.imis.athena-innovation.gr/bufet/reproduction_files.zip
 *
 * tornado uk.ac.manchester.tornado.examples.bufet.BufetBigIndex help
 *
 */

public class BufetBigIndex {

    // ------Const definition segment------
    private static final int genes_population = 25000; // maximum amount of Genes
    private static final int miRNA_groups = 10000; // the amount of random miRNAs target groups

    // ------Methods definition segment------
    // Create a Hash table with unique instances and assign an ID
    public static HashMap<String, Integer> getUniqueInstances(ArrayList<String> inputData) {

        HashMap<String, Integer> map = new HashMap<>();
        int cnt = 0; // First ID is the ZERO

        for (String inputDatum : inputData) {
            if (!map.containsKey(inputDatum)) {
                map.put(inputDatum, cnt++); // Associate key with unique ID value
            }
        }
        return map;
    }

    // Read the differentially expressed miRNAs provided by the user
    public static int[] getMiRNAsPhaseA(String filename, String delimiter, HashMap<String, byte[]> map, HashMap<String, ArrayList<Integer>> goCatGenesUnq) throws IOException {

        ArrayList<String> list = new ArrayList<>(InputScanner.getString(filename, delimiter));
        byte[] array = new byte[genes_population];
        int[] retArray = new int[(2 + goCatGenesUnq.size())]; // temporary store of return values
        int target_genes = 0; // Count ones in array (gene_map)
        int group_found = 0; // miRNA matches between the user-defined miRNA sequence and the Database of
                             // miRNAs
        int idx = 2; // Avoid the first two positions for target_genes and group_found

        for (String token : list) {
            if (map.containsKey(token)) {
                group_found++;
                for (int i = 0; i < genes_population; i++) {
                    array[i] |= map.get(token)[i];
                }
            }
        }
        retArray[0] = group_found; // Position 0: First return value

        for (int i = 0; i < genes_population; i++) {
            if (array[i] == 1) {
                target_genes++;
            }
        }
        retArray[1] = target_genes; // Position 1: Second return value

        for (HashMap.Entry<String, ArrayList<Integer>> entry : goCatGenesUnq.entrySet()) {
            // how many Genes are associated to the specific goCategory
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

    // Read the differentially expressed miRNAs provided by the user
    // Generate the checkGO and nocheckGO data structures
    public static HashMap<String, ArrayList<String>> getMiRNAsPhaseB(HashMap<String, ArrayList<Integer>> goCatGenesUnq, HashMap<String, ArrayList<String>> goCategories, int[] phaseAarr,
            boolean goOrNogo) {

        HashMap<String, ArrayList<String>> map = new HashMap<>();
        int idx = 2;

        for (HashMap.Entry<String, ArrayList<Integer>> entry : goCatGenesUnq.entrySet()) {
            if ((phaseAarr[idx] > 0) && (goOrNogo == true)) { // Generate checkGO
                map.put(entry.getKey(), new ArrayList<String>());
                map.get(entry.getKey()).add(entry.getKey()); // goCategory
                map.get(entry.getKey()).add(Integer.toString(entry.getValue().size())); // Amount of Genes for the goCategory
                map.get(entry.getKey()).add(Integer.toString(phaseAarr[idx])); // intersection
                map.get(entry.getKey()).add(Double.toString((double) phaseAarr[idx] / (double) phaseAarr[1])); // overall_proportion
                map.get(entry.getKey()).add(Double.toString(0.0)); // mean_overlap
                for (String token : goCategories.get(entry.getKey())) {
                    map.get(entry.getKey()).add(token);
                }
            }

            if ((phaseAarr[idx] <= 0) && (goOrNogo == false)) { // Generate nocheckGO
                map.put(entry.getKey(), new ArrayList<String>());
                map.get(entry.getKey()).add(entry.getKey()); // goCategory
                map.get(entry.getKey()).add(Integer.toString(entry.getValue().size())); // Amount of Genes for the goCategory
                map.get(entry.getKey()).add(Integer.toString(0)); // intersection
                map.get(entry.getKey()).add(Double.toString(0.0)); // overall_proportion
                for (String token : goCategories.get(entry.getKey())) {
                    map.get(entry.getKey()).add(token);
                }
            }
            idx++;
        }
        return map;
    }

    // Generate the miRNAs target groups
    public static int[] getRandomTargetGroup(int upperBound, int size, byte[] onlyGeneVector) {

        Random rand = new Random();
        // Compact miRNA bit representation in 32-bits numbers
        int genePopBitLength = (genes_population / 16);
        int[] array = new int[miRNA_groups * genePopBitLength];
        int randIdx = 0; // random index
        int idx = 0;

        for (int k = 0; k < miRNA_groups; k++) {
            for (int i = 0; i < size; i++) {
                int miRNA_groupsCnt = 0; // MAX = 25000
                randIdx = rand.nextInt(upperBound);

                for (int j = 0; j < genePopBitLength; j++) { // 3125; a single miRNA group
                    for (int shiftAmount = 0; shiftAmount < 16; shiftAmount++) { // 8-bits counting
                        idx = ((randIdx * genes_population) + miRNA_groupsCnt);
                        array[((k * genePopBitLength) + j)] |= (onlyGeneVector[idx] << shiftAmount);
                        miRNA_groupsCnt++;
                    }
                }
            }
        }
        return array;
    }

    // Calculate the number of genes targeted by each random miRNA group
    public static short[] calculateCounts(int sizeA, int[] map_all) {

        short[] countOnes = new short[sizeA];
        short value = 0;
        int genePopBitLength = (genes_population / 16);

        for (int i = 0; i < sizeA; i++) {
            value = 0;
            for (int j = 0; j < genePopBitLength; j++) {
                for (int shiftAmount = 0; shiftAmount < 16; shiftAmount++) {
                    value = (short) (value + ((map_all[((i * genePopBitLength) + j)] >> shiftAmount) & 1));
                }
            }
            countOnes[i] = value;
        }
        return countOnes;
    }

    // Calculate the intersections of all random miRNA sets
    // for the candidate GO category and calculate the sets with greater overlap
    public static long[] findIntersections(int size, int[] map_all, short[] countOnes, HashMap<String, ArrayList<String>> checkGO, HashMap<String, ArrayList<Integer>> goCatUniqueGenes) {

        int idx = 0;
        long[] array = new long[size];
        int genePopBitLength = (genes_population / 16);

        for (HashMap.Entry<String, ArrayList<String>> entry : checkGO.entrySet()) {
            double tempMeanOverlap = Double.parseDouble(entry.getValue().get(4));
            double overallProportion = (Double.parseDouble(entry.getValue().get(3)));

            for (int i = 0; i < miRNA_groups; i++) {
                int intersection = 0;
                for (Integer token : goCatUniqueGenes.get(entry.getKey())) {
                    // Convert division token/8 to token >> 3, Convert module token % 8 to (token &
                    // (8 - 1))
                    intersection += ((map_all[((i * genePopBitLength) + (token >> 4))] >> (token & 15)) & 1);
                }

                double overlap = (double) intersection / (double) countOnes[i];
                tempMeanOverlap += overlap;
                if ((overlap >= overallProportion)) {
                    array[idx]++;
                }
            }
            entry.getValue().set(4, Double.toString(tempMeanOverlap));
            idx++;
        }
        return array;
    }

    // Output of the miRNA enrichment process
    public static void WriteOutput(String fileName, ArrayList<Double> pvalueList, ArrayList<String> fdrList, HashMap<String, ArrayList<String>> checkGO, HashMap<String, ArrayList<String>> nocheckGO) {

        ArrayList<String> text = new ArrayList<>(); // text to write to the file
        int i = 0,j = 0;
        double mean_overlap = 0.0;

        text.add("GO-term-ID  GO-term-size  Gene-Overlap ");
        text.add("Mean-Target-Overlap-Proportion  empirical-p-value  Benjamini-Hochberg-0.05-FDR \n");
        text.add("\n");

        for (HashMap.Entry<String, ArrayList<String>> entry : checkGO.entrySet()) {
            text.add(entry.getValue().get(0)); // go Category
            text.add("\t");
            text.add(entry.getValue().get(5)); // name
            text.add("\t");
            text.add(entry.getValue().get(1)); // size
            text.add("\t");
            text.add(entry.getValue().get(3)); // overall_proportion
            text.add("\t");
            mean_overlap = (Double.parseDouble(entry.getValue().get(4)) / (double) miRNA_groups); // mean_overlap
            text.add(Double.toString(mean_overlap));
            text.add("\t");
            text.add(Double.toString(pvalueList.get(i))); // pvalue
            text.add("\t");
            text.add(fdrList.get(i));
            text.add("\n");
            i++;
        }
        for (HashMap.Entry<String, ArrayList<String>> entry : nocheckGO.entrySet()) {
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
        InputScanner.writeString(fileName, text);
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 5) {
            if (args[0].equals("help")) {
                System.out.println("BufetApp Usage");
                System.out.println("Usage tornado [options] class [arg0 ... argN]");
                System.out.println(
                        "Command: tornado uk.ac.manchester.tornado.examples.bufet.BufetBigIndex miRanda_dataset.csv \"\\\\n|\\|\" annotation_dataset.csv \"\\\\n|\\|\" miRNA-5.txt output_file.txt");

            } else {
                System.out.println("Error: Arguments are missing (Pass " + args.length + " out of 5)!");
            }
            System.exit(0);
        }

        // Store interactions between miRNAs and Genes
        System.out.println("Reading " + args[0] + " and generate a Hashmap for miRNA-Gene interactions.");
        HashMap<String, ArrayList<String>> miRNA_Genes = new HashMap<>(InputScanner.readString(args[0], args[1], 0, 1, false));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for unique Genes.");
        HashMap<String, Integer> genes = new HashMap<>(getUniqueInstances(InputScanner.getString(args[2], args[3])));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for Go categories.");
        // Store only the first instance of the go Category name
        HashMap<String, ArrayList<String>> goCategories = new HashMap<>(InputScanner.readString(args[2], args[1], 1, 2, true));

        System.out.println("Reading " + args[2] + " and generate a Hashmap for GoGenes <goCategory, Gene, ...>.");
        HashMap<String, ArrayList<String>> goCatGenes = new HashMap<>(InputScanner.readString(args[2], args[1], 1, 0, true));

        // Each go Category contains a vector of Gene IDs
        HashMap<String, ArrayList<Integer>> goCatUniqueGenes = new HashMap<>();
        for (HashMap.Entry<String, ArrayList<String>> entry : goCatGenes.entrySet()) {
            goCatUniqueGenes.put(entry.getKey(), new ArrayList<Integer>());
            for (String token : entry.getValue()) {
                if (genes.containsKey(token)) {
                    goCatUniqueGenes.get(entry.getKey()).add(genes.get(token));
                }
            }
        }

        // Each miRNA is associated with a bit vector of active Genes
        HashMap<String, byte[]> interactions = new HashMap<>();
        for (HashMap.Entry<String, ArrayList<String>> entry : miRNA_Genes.entrySet()) {
            interactions.put(entry.getKey(), new byte[genes_population]);
            for (String token : entry.getValue()) {
                if (!genes.containsKey(token)) {
                    // Append the new gene to Genes' structure
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

        // Store the miRNA target group for the user-defined miRNA sequence
        System.out.println("Generate miRNA target group for the user-defined miRNA sequence (Phase A, and B).");
        int[] getMiRNAsretVal;
        getMiRNAsretVal = getMiRNAsPhaseA(args[4], args[1], interactions, goCatUniqueGenes);

        HashMap<String, ArrayList<String>> checkGO;
        checkGO = getMiRNAsPhaseB(goCatUniqueGenes, goCategories, getMiRNAsretVal, true);

        HashMap<String, ArrayList<String>> nocheckGO;
        nocheckGO = getMiRNAsPhaseB(goCatUniqueGenes, goCategories, getMiRNAsretVal, false);

        // Cast Hash table to an array of byte values
        byte[] onlyGeneVector = new byte[genes_population * interactions.size()];
        int j = 0; // aux index to parse array
        for (HashMap.Entry<String, byte[]> entry : interactions.entrySet()) { // interactions.size() iterations
            int i = 0;
            for (byte token : entry.getValue()) { // gene_population iterations
                // A new miRNA target group is stored every genes_population byte values
                onlyGeneVector[(j * genes_population) + i] = token;
                i++;
            }
            j++;
        }

        System.out.println("Generate the miRNAs target groups.");
        int[] map_all;
        map_all = getRandomTargetGroup(miRNA_Genes.size(), getMiRNAsretVal[0], onlyGeneVector);

        System.out.println("Count ones in each miRNA target group.");
        short[] countOnes;
        countOnes = calculateCounts(miRNA_groups, map_all);

        System.out.println("Calculate the intersections for all random miRNA sets");
        int sizeB = checkGO.size(); // Get the number of unique categories

        long[] pvalues;
        pvalues = findIntersections(sizeB, map_all, countOnes, checkGO, goCatUniqueGenes);

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
