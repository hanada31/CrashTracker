import os 
import shutil
from ExplanationGenerator.config import PRE_CHECK_RESULT_PATH, RESULT_SUMMARY_PATH, EXPLANATION_RESULT_PATH, logger, add_file_handler, EXPLANATION_RESULT_DIRECTORY
from ExplanationGenerator.extractor import query_framework_constraint
from ExplanationGenerator.explainer import explain_candidates
from ExplanationGenerator.exceptions import ConstraintCheckError, TooMuchCandidateError
import json

def result_dirs(report_name):
    result_dir = EXPLANATION_RESULT_PATH(report_name)
    os.makedirs(result_dir, exist_ok=True)
    shutil.copy(f"{PRE_CHECK_RESULT_PATH(report_name)}/report_info.json", f"{result_dir}/report_info.json")
    constraint_result_dir = f"{result_dir}/constraint"
    os.makedirs(constraint_result_dir, exist_ok=True)
    explanation_result_dir = f"{result_dir}/explanation"
    os.makedirs(explanation_result_dir, exist_ok=True)
    report_info_path = f"{result_dir}/report_info.json"
    return result_dir, constraint_result_dir, explanation_result_dir, report_info_path

def main():
    add_file_handler(EXPLANATION_RESULT_DIRECTORY / "explanation.log")
    if not os.path.exists(RESULT_SUMMARY_PATH):
        logger.error(f"Result summary file {RESULT_SUMMARY_PATH} does not exist")
        exit(1)
    else:
        result_summary = json.loads(open(RESULT_SUMMARY_PATH, 'r').read())
    for report_name in result_summary["pending_list"].keys():
        if report_name in result_summary["reports"]:
            continue

        result_dir, constraint_result_dir, explanation_result_dir, report_info_path = result_dirs(report_name)
        with open(report_info_path, "r") as f:
            report_info = json.load(f)
        logger.info(f"Start processing {report_name}")

        try:
            logger.info("Querying framework constraint")
            constraint, bypass_count, query_count, cache_hit_count = query_framework_constraint(report_info, constraint_result_dir)
            logger.info(f"Successfully query constraint! Constraint: {constraint}")
            explain_candidates(report_info, constraint, explanation_result_dir)
            logger.info(f"Successfully explained candidates!")
            result_summary["reports"][report_name] = {
                "status": "finished",
                "query_count": query_count,
                "bypass_count": bypass_count,
                "cache_hit_count": cache_hit_count
            }
            result_summary["statistic"]["query_count"] = result_summary["statistic"].get("query_count", 0) + query_count
            result_summary["statistic"]["bypass_count"] = result_summary["statistic"].get("bypass_count", 0) + bypass_count
            result_summary["statistic"]["cache_hit_count"] = result_summary["statistic"].get("cache_hit_count", 0) + cache_hit_count
        except ConstraintCheckError as e:
            logger.error(e)
            result_summary["reports"][report_name] = {
                "status": "failed",
                "error": str(e)
            }
            shutil.rmtree(result_dir)
        except TooMuchCandidateError as e:
            logger.error(e)
            result_summary["reports"][report_name] = {
                "status": "failed",
                "error": str(e)
            }
            shutil.rmtree(result_dir)

        with open(RESULT_SUMMARY_PATH, "w") as f:
            json.dump(result_summary, f, indent=4)
        logger.info(f"Finished processing {report_name}")


if __name__ == "__main__":
    main()