/*
 * Copyright (c) 2020, 2023, APT Group, Department of Computer Science,
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

const express = require('express')
const publicDir = require('path').join(__dirname,'/');
const app = express();
const fs = require('fs')

app.use(express.static(publicDir));
const Arrays = Java.type('java.util.Arrays')

function getNanoSecTime() {
	var hrTime = process.hrtime();
	return hrTime[0] * 1000000000 + hrTime[1];
}

// It calls Tornado to compute mandelbrot on a GPU
app.get('/', function (req, res) {
	var text = "Hello World from Graal JS! "
	text += "<br>"
	text += Java.type('uk.ac.manchester.tornado.examples.polyglot.node.Mandelbrot').getString()
	text += "<br>"
	var start = getNanoSecTime()
	Arrays.toString(Java.type('uk.ac.manchester.tornado.examples.polyglot.node.Mandelbrot').compute())
	var end = getNanoSecTime()
	text += "<br>"
	text += "Total time (s) = " + ( (end - start) * 1E-9)
	text += "<br>"

	// Render resulting image 
	text += "<img src=\"/mandelbrot.png\"></img>"
	res.send(text)
})

// It calls Java to compute mandelbrot 
app.get('/java', function (req, res) {
	var text = "Hello World from Graal JS! "
	text += "<br>"
	text += Java.type('uk.ac.manchester.tornado.examples.polyglot.node.Mandelbrot').getString()
	text += "<br>"
	var start = getNanoSecTime()
	Arrays.toString(Java.type('uk.ac.manchester.tornado.examples.polyglot.node.Mandelbrot').sequential())
	var end = getNanoSecTime()
	text += "<br>"
	text += "Total time (s) = " + ( (end - start) * 1E-9)
	text += "<br>"

	// Render resulting image 
	text += "<img src=\"/mandelbrot.png\"></img>"
	res.send(text)
})

// Creates a node express server on port 3000
app.listen(3000, function() {
	console.log("The application is listening on port 3000. ");
})
