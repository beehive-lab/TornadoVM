Thank you for contributing to TornadoVM. This template provides checkpoints before opening PR, and a template to be filled when submitting a PR on GitHub.

## IMPORTANT: Before submitting the PR

At first, **please check our code style and guidelines** for contributing to the project.

* Guidelines: [link](https://github.com/beehive-lab/TornadoVM/blob/master/CONTRIBUTING.md)

All PRs must be open to merge into the `develop` branch of TornadoVM. Please, do not push the PR into `master`.

All unittests must pass. Note that some unittests may fail depending on the driver and runtime used. 
They are listed in a white-list in the `tornado-test` script. 

```bash
$ make tests
```

TornadoVM currently supports three backends. When possible, please check that the new changes do not break any backend.

```bash
## Pass Unittests using the OpenCL backend
$ make BACKEND=opencl
$ make tests

## If the changes are also applicable to the PTX backend: 
## Pass unittests using the PTX backend
$ make BACKEND=ptx
$ make tests 

## If the changes are also applicable to the SPIR-V backend: 
## Pass unittests using the SPIRV backend
$ make BACKEND=spirv
$ make tests 
```

Once all unit-tests pass, please fill the following template for the Pull Request:

----------------------------------------------------------------------------

## Template to be used in the PR description (remove this part when submitted the PR)

#### Description

Describe the patch. What does it enhance? What does it fix?

#### Problem description

If the patch provides a fix for a bug, please describe what was the issue and how to reproduce the issue.

#### Backend/s tested

Mark the backends affected by this PR.

- [ ] OpenCL
- [ ] PTX
- [ ] SPIRV

#### OS tested

Mark the OS where this PR is tested.

- [ ] Linux
- [ ] OSx
- [ ] Windows

#### Did you check on FPGAs?

If it is applicable, check your changes on FPGAs.

- [ ] Yes
- [ ] No

#### How to test the new patch?

Provide instructions about how to test the new patch. 

----------------------------------------------------------------------------
