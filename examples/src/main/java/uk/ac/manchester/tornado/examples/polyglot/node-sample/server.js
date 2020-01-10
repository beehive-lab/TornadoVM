const express = require('express')
const app = express()

const Arrays = Java.type('java.util.Arrays')

app.get('/', function (req, res) {
	var text = "Hello World from Graal JS! "
	text += "<br>"
	text += Java.type('Compute').getString()
	text += "<br>"
	text += Arrays.toString(Java.type('Compute').compute())

	res.send(text);
})

app.listen(3000, function() {
	console.log("The application is listening on port 3000");
})