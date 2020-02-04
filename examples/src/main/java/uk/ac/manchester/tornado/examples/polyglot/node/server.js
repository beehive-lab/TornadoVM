// Copyright (c) 2020, APT Group, Department of Computer Science,
// School of Engineering, The University of Manchester. All rights reserved.
// Copyright (c) 2019, APT Group, School of Computer Science,
// The University of Manchester.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


const express = require('express')
const app = express()

const Arrays = Java.type('java.util.Arrays')

// Request mapping for localhost:3000/
app.get('/', function (req, res) {
	var text = "Hello World from Graal JS! "
	text += "<br>"
	text += Java.type('Compute').getString()
	text += "<br>"
	text += Arrays.toString(Java.type('Compute').compute())

	res.send(text);
})

// Creates a node express server on port 3000
app.listen(3000, function() {
	console.log("The application is listening on port 3000");
})