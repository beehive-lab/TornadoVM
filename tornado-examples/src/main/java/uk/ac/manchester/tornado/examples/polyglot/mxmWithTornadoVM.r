java.addToClasspath("uk.ac.manchester.tornado.examples.polyglot.MyCompute")

comp <- java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

for (i in 1:5) {
    start <- Sys.time()
    comp$compute()
    end <- Sys.time()
    print(end - start)
}