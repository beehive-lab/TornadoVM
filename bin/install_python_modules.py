#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import subprocess


def check_python_dependencies():
    """
    Check the required dependencies for the installation of TornadoVM.
    """
    
    try:
        import requests
    except:
        subprocess.call(["pip3", "install", "requests"], stderr=subprocess.DEVNULL)
        import requests
    
    try:
        import tqdm
    except:
        subprocess.call(["pip3", "install", "tqdm"], stderr=subprocess.DEVNULL)
        import tqdm
    
    try:
        import urllib3
    except:
        subprocess.call(["pip3", "install", "urllib3"], stderr=subprocess.DEVNULL)
        import urllib3
    
    try:
        import wget
    except:
        subprocess.call(["pip3", "install", "wget"], stderr=subprocess.DEVNULL)
        import wget

    try:
        import packaging
    except:
        subprocess.call(["pip3", "install", "packaging"], stderr=subprocess.DEVNULL)
        import packaging

    try:
        import streamlit
    except:
        subprocess.call(["pip3", "install", "streamlit"], stderr=subprocess.DEVNULL)
        import streamlit

    return 0
    
