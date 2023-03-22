java.addToClasspath("uk.ac.manchester.tornado.examples.polyglot.MyCompute")

comp <- java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

print("Hello TornadoVM from R!")
for (i in 1:5) {
    start <- Sys.time()
    comp$compute()
    end <- Sys.time()
    print(paste("Total time (s): ", end - start))
}