all: default_install

default_install:
	python easy-install.py

example:
	tornado tornado.examples.HelloWorld

eclipse:
	mvn eclipse:eclipse
