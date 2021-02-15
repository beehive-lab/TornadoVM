Thank you for contributing to TornadoVM. This template provides checkpoints before the PR and a template to be filled
when submitting a PR on Github.

## Before submitting the PR

Before continuing with the PR, please check our code style and guidelines for contributing to the project.

* Guidelines: [link](https://github.com/beehive-lab/TornadoVM/blob/master/CONTRIBUTING.md)

All PRs must be open to merge into the `develop` branch of TornadoVM. Please, do not push the PR into `master`.

All unittests must pass.

```bash
$ make tests
```

TornadoVM currently supports two backends. When possible, please check that the new changes do not break any backend.

```bash
## Pass Unittests using the OpenCL backend
$ make
$ make tests

## Pass unittests using the PTX backend
$ make BACKEND=ptx,opencl
$ make tests # It will select the PTX by default 
```

## Template to be used in the PR description

#### Description

Describe the patch. What does it enhance? What does it fix?

#### Problem description

If the patch provides a fix for a bug, please describe what was the issue and how to reproduce the issue.

#### Backend/s tested

- [ ] OpenCL
- [ ] PTX

#### OS tested

- [ ] Linux
- [ ] OSx
- [ ] Windows

#### Did you check on FPGAs?

If possible, check your changes on FPGAs.

- [ ] Yes
- [ ] No

#### How to test the new patch?

Provide instructions about how to test the new patch. 
