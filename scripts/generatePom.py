#!/usr/bin/python

import os

__TORNADO_ENV_FILE__ = "etc/tornado.env"

def generateXMLPomFile():

	try:
		os.path.isfile(__TORNADO_ENV_FILE__)
	except:
		print "File " + __TORNADO_ENV_FILE__ + " does not exist. Please provide the config file"
		sys.exit(-1)


	graal_home = os.environ["GRAAL_ROOT"]
	java_home = os.environ["JAVA_HOME"]

	pomfile = open("scripts/templates/pom.xml").read()

	pomfile = pomfile.replace("%%%GRAAL_ROOT%%%", graal_home)
	pomfile = pomfile.replace("%%%JAVA_HOME%%%", java_home)

	f = open("pom.xml", "w")
	f.write(pomfile)

	f.close()


generateXMLPomFile()
