import subprocess

def run_git_command(command):
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, text=True)
        return result.stdout.strip().split("\n")
    except subprocess.CalledProcessError as e:
        print(f"Error executing command: {e.stderr}")
        return []

def run_mvn_checkstyle(file):
    mvn_command = ['mvn', 'checkstyle:check', f'-Dincludes="{file}"']
    try:
        subprocess.run(mvn_command, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Error executing command: {e.stderr}")

# Define the git command
git_command = ['git', 'diff', '--diff-filter=d', '--cached', '--name-only']

# Run the git command and get the list of changed files
changed_files = run_git_command(git_command)

# Iterate over the list and run mvn checkstyle for each file
for file in changed_files:
    print(f"Running mvn checkstyle for file: {file}")
    run_mvn_checkstyle(file)

