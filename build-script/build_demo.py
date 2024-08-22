import shutil
from pathlib import Path
import subprocess

def main():
    cwd = Path.cwd()
    build_dir = cwd / ".build"
    build_dir.mkdir(exist_ok=True, parents=True)

    crash_tracker_fl = cwd / "CrashTracker_FL.jar"
    if not crash_tracker_fl.exists():
        # mvn -f pom.xml clean package -DskipTests
        subprocess.run(['mvn', '-f', 'pom.xml', 'clean', 'package', '-DskipTests'], check=True)
        shutil.copy2(cwd / "target" / "CrashTracker-jar-with-dependencies.jar", cwd / "CrashTracker_FL.jar")
    
    shutil.copy2(cwd / "CrashTracker_FL.jar", build_dir)
    
    # Copy ExplanationGenerator directory
    shutil.copytree(cwd / "ExplanationGenerator", build_dir / "ExplanationGenerator", dirs_exist_ok=True)
    # Remove __pycache__ directories
    for pycache in build_dir.rglob("__pycache__"):
        shutil.rmtree(pycache)
    
    # Copy .env.template file
    shutil.copy2(cwd / ".env.template", build_dir)
    shutil.copy2(cwd / "requirements.txt", build_dir)

    shutil.copytree(cwd / "input", build_dir / "input", dirs_exist_ok=True)
    shutil.copytree(cwd / "references", build_dir / "references", dirs_exist_ok=True)


if __name__ == '__main__':
    main()
