# Installation Process of Tornado SDK


### Prerequisites

* Java 8 with JVMCI Support; we created the following prebuilt versions:
  * [For Linux x64_64](https://www.dropbox.com/s/nvtpsviqc6u8vnv/jdk1.8.0_131_x86.tgz?dl=0)
  * [For OSX](https://www.dropbox.com/s/2aguj98jg5b5yh4/jdk1.8.0_131-osx-10.11.6.tgxz?dl=0)
* OpenCL >= 1.2


### Configuration


__Obtain the Tornado SDK for:__
* [For Linux x64_64](https://drive.google.com/file/d/10MjvCC3VmOecGtD1lwKd7Sp8HAV_i5AB/view?usp=sharing)
* [For OSX](https://drive.google.com/file/d/1OV23CJqrYk64an-7gdIK7Uoh2ssJB-dp/view?usp=sharing)

__Set up the Tornado working directory:__

```bash
$ mkdir <your_work_dir>
$ cd <your_work_dir>
$ cp tornado-sdk-<linux|osx>.tar.gz <your_work_dir>
$ tar xvzf tornado-sdk-<linux|osx>.tar.gz
$ cd tornado-sdk-0.0.2-SNAPSHOT-*
```

__We need to set the following 3 env variables (JAVA_HOME, PATH, TORNADO_SDK):__

1. Set `JAVA_HOME` to JDK


```bash
export JAVA_HOME=path/to/jdk1.8.0_131
```


2. Set a new `PATH` and `TORNADO_SDK`

```bash
export PATH=<workdir>/tornado-sdk-0.0.2-SNAPSHOT-<ID>/bin/:$PATH
export TORNADO_SDK=<workdir>/tornado-sdk-0.0.2-SNAPSHOT-<ID>
```

Note, the ID is the git-version in Tornado. 


and done!

# Check Installation 

```bash
$ tornado
Usage: java [-options] class [args...]
           (to execute a class)
   or  java [-options] -jar jarfile [args...]
           (to execute a jar file)
where options include:
    -d32	  use a 32-bit data model if available
    -d64	  use a 64-bit data model if available
    -server	  to select the "server" VM
    -original	  to select the "original" VM
                  The default VM is server,
                  because you are running on a server-class machine.


    -cp <class search path of directories and zip/jar files>

...
```

__Testing__


Tornado provides a sets of unittests. You can run them using as follows:


```bash
tornado-test.py -V
```

Note: Not all of them are currently passing; expect around 4 or 5 to fail. 



