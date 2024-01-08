#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import csv
import sys


def split(infile, outfile):
    with open(infile, "r") as f:
        reader = f.readlines()
        with open(outfile, "w") as out:
            o = csv.writer(out)
            for row in reader:
                line = row.split(",")
                temp = []
                first = line[0].split("=")[1].strip()
                lista = first.split("-")
                for a in lista:
                    temp.append(a)
                for el in line[1:]:
                    asdf = el.split("=")
                    temp.append(asdf[1].strip())
                o.writerow(temp)


def main(outputExtension="out2csv.txt"):
    filename = sys.argv[1]
    split(filename, outputExtension)


if __name__ == "__main__":
    main()
