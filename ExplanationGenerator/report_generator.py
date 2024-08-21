from ExplanationGenerator.utils.parser import parse_message
from ExplanationGenerator.config import logger
from ExplanationGenerator.config import EXPLANATION_RESULT_PATH, EXPLANATION_RESULT_DIRECTORY, FINAL_REPORT_PATH, APK_CODE_PATH, REPORT_TEMPLATE_PATH, ROOT_PATH
from ExplanationGenerator import survery_global_summary_generator
from pathlib import Path
import shutil
import json
import subprocess


def write_full_class_file(report_name, snippet, survey_path: Path):
    """
    write related code file
    """
    import re
    class_file_path = survey_path
    class_file_path.mkdir(parents=True, exist_ok=True)
    signatures = re.findall(r"(public|private)(\s\w+)+\s(\w+)\(", snippet)
    if len(signatures) == 0:
        return
    visibility, other, method_name = signatures[0]

    report_info_path = Path(EXPLANATION_RESULT_PATH(report_name)) / "report_info.json"
    report_info = json.load(report_info_path.open())
    for candidate in report_info["candidates"]:
        if candidate["Candidate Name"].split(".")[-1] == method_name:
            full_name: str = candidate["Candidate Name"]
            names = full_name.split(".")
            path = APK_CODE_PATH(report_name)
            for name in names:
                if "$" in name:
                    name = name.split("$")[0]
                path = path / name
                path_with_java = path.with_suffix(".java")
                if path_with_java.exists() and path_with_java.is_file():
                    shutil.copy2(path_with_java, class_file_path)
                    return
            logger.error(f"Cannot find the class file, path: {path}")
    logger.error(f"Cannot find the full class file for {method_name} in {report_name}")


def generate_global_explanation():
    pass


def pairwise(iterable):
    "s -> (s0, s1), (s2, s3), (s4, s5), ..."
    if len(iterable) % 2 != 0:
        raise ValueError("Length of iterable must be even")
    a = iter(iterable)
    return zip(a, a)

def main():
    for dir in EXPLANATION_RESULT_DIRECTORY.iterdir():
        if dir.is_file():
            continue
        report_name = dir.name
        result_path = dir
        survey_path = FINAL_REPORT_PATH(report_name)
        survey_path.mkdir(parents=True, exist_ok=True)

        report_info = result_path / "report_info.json"
        shutil.copy2(report_info, survey_path)

        candidate_conversation_path = result_path / "explanation" / "candidate_conversation.json"
        with open(candidate_conversation_path, mode='r') as f:
            candidate_conversation = json.load(f)
        candidate_conversation = candidate_conversation[2:]
        if len(candidate_conversation) % 2 != 0:
            raise ValueError("Length of candidate_conversation must be even")
        candidate_num = len(candidate_conversation) // 2
        if candidate_num == 0:
            shutil.rmtree(survey_path)
            logger.error(f"No candidate explanation found in {report_name}")
            continue
        for index, (user_message, assistant_message) in enumerate(pairwise(candidate_conversation)):
            user_parsed = parse_message(user_message['content'])
            assistant_parsed = parse_message(assistant_message['content'])
            candidate_path  = survey_path / str(index)
            candidate_path.mkdir(parents=True, exist_ok=True)

            snippet = user_parsed["Suspicious_Method"]
            write_full_class_file(report_name, snippet, candidate_path)
            explanation = assistant_parsed["Explanation"]
            with open(candidate_path / "snippet.txt", mode='w') as f:
                f.write(snippet)
            with open(candidate_path / "explanation.txt", mode='w') as f:
                f.write(explanation)
        
        constraint_path = result_path / "constraint" / "constraint.txt"
        survey_constraint_path = survey_path / "constraint.txt"
        shutil.copy2(constraint_path, survey_constraint_path)

        if (survey_path / "global_summary.txt").exists():
            logger.info(f"Global summary for {report_name} already exists, skipping")
        survery_global_summary_generator.main(report_name)

        command = [
            "pandoc",
            "-s",
            "-f",
            "markdown",
            "--wrap=none",
            "-t",
            "commonmark",
            f"--metadata=report_name:{report_name}",
            "--metadata=report_type:llm",
            f"--metadata=report_dir:{survey_path.parent}",
            "--filter",
            ROOT_PATH / "report_fill.py",
            "-o",
            survey_path / "report.md",
            REPORT_TEMPLATE_PATH
        ]
        
        subprocess.run(command)

if __name__ == "__main__":
    main()
