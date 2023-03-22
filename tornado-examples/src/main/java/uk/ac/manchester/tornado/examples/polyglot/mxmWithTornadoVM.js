Java.addToClasspath("tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/mxmWithTornadoVM.js")

console.log("Hello TornadoVM from JavaScript!")

for (var i = 0; i < 5; i++) {
    var comp = Java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')
    var start = new Date().getTime() / 1000;
    comp.compute()
    var end = new Date().getTime() / 1000;
    console.log("Total Time (s): " + (end - start))
}