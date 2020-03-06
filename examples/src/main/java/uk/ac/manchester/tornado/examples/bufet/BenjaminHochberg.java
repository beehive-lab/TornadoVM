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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class BenjaminHochberg {

    private ArrayList<Double> pvalueList = new ArrayList<>();
    private ArrayList<Double> indexList = new ArrayList<>();
    private ArrayList<String> fdrList = new ArrayList<>();

    public void benjaminHochberg (HashMap<String, ArrayList<String>> checkGO, HashMap<String, ArrayList<String>>  nocheckGO, long[] pvalues, int miRNA_groups) {

        int totalGo = checkGO.size();
        int totalNoGo = nocheckGO.size();
        int maxI = 0;
        double star = 0.05, doubleStar = 0.01;

        for (int i = 0; i < totalGo; i++) {
            pvalueList.add((double)pvalues[i] / (double)miRNA_groups);
            //indexList.add(i);
            indexList.add((double)pvalues[i] / (double)miRNA_groups);
            fdrList.add("");
        }

        for (int i = 0; i < totalNoGo; i++) {
            pvalueList.add(1.0);
            //indexList.add(i + totalGo);
            indexList.add(1.0);
            fdrList.add("");
        }

        Collections.sort(pvalueList);
        int pNodeListSize = pvalueList.size();

        for (int i = 0; i < pNodeListSize; i++) {
            double starcheck = (double)(((i + 1) * star)) / (double)pNodeListSize;
            if (pvalueList.get(i) <= starcheck) {
                maxI = i;
            }
        }

        if (maxI > pNodeListSize) {
            maxI = pNodeListSize;
        }

        for (int i = 0; i < maxI; i++) {
            fdrList.set(i,"*");
        }

        for (int i = 0; i < pNodeListSize; i++) {
            double starcheck = (double)((i + 1) * doubleStar) /  (double)pNodeListSize;
            if (pvalueList.get(i) <= starcheck) {
                maxI = i;
            }
        }

        if (maxI > pNodeListSize) {
            maxI = pNodeListSize;
        }

        for (int i = 0; i < maxI; i++) {
            fdrList.set(i,"**");
        }

        pvalueList = indexList;
    }

    public ArrayList<Double> returnPvalueList() {

        return pvalueList;
    }

    public ArrayList<String> returnFdrList() {

        return fdrList;
    }

}