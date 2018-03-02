#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornado
#
# Copyright (c) 2013-2018, APT Group, School of Computer Science,
# The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Authors: James Clarkson
#

import csv
import re
import sys
import subprocess

def loadBCMappings(mappingfile):
    mappings = {}
    with open(mappingfile) as csvfile:
		rowreader = csv.reader(csvfile,delimiter=",")
		for row in rowreader:
			mappings[row[0]] = row[1]
    if "name" in mappings:
        del mappings["name"]
    return mappings

def loadInverseBMMappings(bcMappings):
    mappings = {}
    for key in bcMappings:
        mappings[bcMappings[key]] = key
    return mappings


def insert(values, newValue):
    if newValue in values:
        values[newValue] += 1
    else:
        values[newValue] = 1

def loadDisasm(disasmFile):
    mappings = {}
    with open(disasmFile) as f:
        for line in f:
            values = line.split(" ")
            if(len(values) > 1):
                insert(mappings,values[1].strip())
    return mappings

def toMappings(disasm):
    mappings = {}
    for line in disasm:
        values = line.split(" ")
        if(len(values) > 1):
            insert(mappings,values[1].strip())
    return mappings

def formatOutput(bcMappings, disasmMappings):
    for key in sorted(bcMappings):
        count = 0
        op = bcMappings[key]
        if op in disasmMappings:
            count = disasmMappings[op]
        print '0x%s,%3d,%s' % (key,count,op)

def toFile(file,id,bcMappings, disasmMappings):
    #file.write("%s " % id)
    #file.write("value,count,name\n");
    x = "%s" % (id)
    for key in sorted(bcMappings):
        count = 0
        op = bcMappings[key]
        if op in disasmMappings:
            count = disasmMappings[op]
        x = '%s %d' % (x,count)
    file.write('%s\n' % (x.strip().replace(" ",",")))

def findDisasm(method, output):
    res = []
    lines = output.split("\n")
    p = re.compile("^.* %s\(" % method.strip())
    p1 = re.compile('^.*: return')
    p2 = re.compile('^\s*Code:')
    found = False
    startCapture = False
    for line in lines:
        
        if startCapture:
            res.append(line.strip())
            if p1.match(line):
                startCapture = False
        
        if found:
            startCapture = p2.match(line)
            found = not startCapture
    
        if p.match(line):
            found = True
    return res

def printFileHeader(file,value2op):
    x = "method"
    for key in sorted(value2op):
        x = '%s %s' % (x,value2op[key])
    file.write('%s\n' % (x.strip().replace(" ",",")))




def getDisasm(declaringClass, method):
    out = subprocess.check_output(['javap', '-c', declaringClass])
    return findDisasm(method,out)

def genMappings(outFile,value2op, declaringClass, method):
    canonicalName = declaringClass.replace('/','.')
    print "mapping %s %s" % (canonicalName[1:-1],method)
    disasm = getDisasm(canonicalName[1:-1],method)
    mappings = toMappings(disasm)
    toFile(outFile,canonicalName[1:-1] + ":" + method.strip(),value2op,mappings)

def printMethods(file,value2op):
    methods = []
    outFile = file + ".bc"
    with open(file) as f:
        with open(outFile,'w') as ofile:
            printFileHeader(ofile,value2op)
            for line in f:
                values = line.split(",")
                if(len(values) == 2):
                    genMappings(ofile,value2op,values[0],values[1])


op2value = loadBCMappings("./bytecode.table2")
value2op = loadInverseBMMappings(op2value)
#print mappings

f = sys.argv[1]
printMethods(f,value2op)
                                #disasm = loadDisasm(f)
#print disasm

#formatOutput(value2op,disasm)
