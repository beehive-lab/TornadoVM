import os
import subprocess
import sys

def check_java_version():
    java_home = os.environ.get("JAVA_HOME")
    java_cmd = os.path.join(java_home, "bin", "java")
    java_version_output = subprocess.check_output([java_cmd, "-version"], stderr=subprocess.STDOUT, universal_newlines=True)
    java_version = java_version_output.splitlines()[0].split()[2].strip('"')
    return java_version, java_version_output

def pull_graal_jars():
    subprocess.run(["python3", "./bin/pullGraalJars.py"])

def maven_cleanup():
    print("mvn -Popencl-backend,ptx-backend,spirv-backend clean")
    subprocess.run(["mvn", "-Popencl-backend,ptx-backend,spirv-backend", "clean"])

def process_backends(selected_backends):
    selected_backends_list = selected_backends.split(",")
    for i, backend in enumerate(selected_backends_list):
        selected_backends_list[i] = f"{backend}-backend"
    selected_backends_str = ",".join(selected_backends_list)
    return selected_backends_str

def build_spirv_toolkit_and_level_zero(selected_backends):
    current = os.getcwd()
    spirvToolkit = "beehive-spirv-toolkit"

    if not os.path.exists(spirvToolkit):
        subprocess.run(["git", "clone", "https://github.com/beehive-lab/beehive-spirv-toolkit.git"])

    os.chdir(spirvToolkit)
    subprocess.run(["git", "pull", "origin", "master"])
    subprocess.run(["mvn", "clean", "package"])
    subprocess.run(["mvn", "install"])
    os.chdir(current)

    levelZeroLib = "level-zero"

    if not os.path.exists(levelZeroLib):
        subprocess.run(["git", "clone", "https://github.com/oneapi-src/level-zero"])
        os.chdir(levelZeroLib)
        os.mkdir("build")
        os.chdir("build")
        subprocess.run(["cmake", ".."])
        subprocess.run(["cmake", "--build", ".", "--config", "Release"])
        os.chdir(current)

    os.environ["ZE_SHARED_LOADER"] = os.path.join(current, "level-zero/build/lib/libze_loader.so")
    os.environ["CPLUS_INCLUDE_PATH"] = os.path.join(current, "level-zero/include") + ":" + os.environ.get("CPLUS_INCLUDE_PATH", "")
    os.environ["C_INCLUDE_PATH"] = os.path.join(current, "level-zero/include") + ":" + os.environ.get("C_INCLUDE_PATH", "")
    os.environ["LD_LIBRARY_PATH"] = os.path.join(current, "level-zero/build/lib") + ":" + os.environ.get("LD_LIBRARY_PATH", "")
    return current

def build_tornadovm(jdk, selected_backends_str, offline):
    options = f"-T1.5C -Dcmake.root.dir=$CMAKE_ROOT -P{jdk},{selected_backends_str} "
    if offline == "OFFLINE":
        options = "-o " + options

    print(f"mvn {options} install")
    return subprocess.run(["mvn"] + options.split() + ["install"])


def post_installation_actions(selected_backends_str, java_version_output, mvn_build_result, jdk):
    if mvn_build_result.returncode == 0:
        # Update all PATHs
        subprocess.run(["python3", "bin/updatePaths.py"], stdout=subprocess.PIPE)

        # Update the compiled backends file

        with open(f"{os.environ['TORNADO_SDK']}/etc/tornado.backend", "w") as backend_file:
            backend_file.write(f"tornado.backends={selected_backends_str}")

        # Place the Graal jars in the TornadoVM distribution only if the JDK 17+ rule is used
        if jdk == "jdk-17-plus" and "GraalVM" not in java_version_output:
            graal_jars_dir = os.path.join(os.getcwd(), "graalJars")
            os.makedirs(f"{os.environ['TORNADO_SDK']}/share/java/graalJars", exist_ok=True)
            subprocess.run(["cp", "-r", f"{graal_jars_dir}/*", f"{os.environ['TORNADO_SDK']}/share/java/graalJars/"])
    else:
        print("\nCompilation failed\n")

def main():
    if len(sys.argv) < 3:
        print("Usage: python script.py <JDK> <backends> (Optional)<offline>")
        sys.exit(1)

    jdk = sys.argv[1]
    selected_backends = sys.argv[2]
    offline = sys.argv[3] if len(sys.argv) > 3 else ""

    java_version, java_version_output = check_java_version()

    if jdk == "jdk-17-plus" and "GraalVM" not in java_version_output:
        pull_graal_jars()

    maven_cleanup()

    selected_backends_str = process_backends(selected_backends)

    if "spirv" in selected_backends:
        build_spirv_toolkit_and_level_zero(selected_backends)

    mvn_build_result = build_tornadovm(jdk, selected_backends_str, offline)

    post_installation_actions(selected_backends_str, java_version_output, mvn_build_result, jdk)

if __name__ == "__main__":
    main()
