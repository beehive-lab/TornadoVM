#!/usr/bin/python

print("Running with tornadoVM")
import java
import time
myclass = java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

print("Hello TornadoVM from Python!")

for i in range(5):
    start = time.time()
    output = myclass.compute()
    end = time.time()
    print("Total time (s): " + str((end-start)))