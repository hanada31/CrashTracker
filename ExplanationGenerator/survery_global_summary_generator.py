# 生成问卷的整体解释
from ExplanationGenerator.config import EXPLANATION_RESULT_PATH, logger, FINAL_REPORT_DIRECTORY, FINAL_REPORT_PATH
from pathlib import Path
import json

# SURVEY_ROOT = Path("RQ/survey")
# SURVEY_PATH: Path = lambda report_name: SURVEY_ROOT / report_name
# PENDING_SURVEY_PATH = Path("RQ/pending_report.json")
SUMMARY_FILE_NAME = "global_summary.txt"
SUMMARY_MESSAGE_FILE_NAME = "global_summary_message.json"

def candidate_reason(candidate, key_api_effects, report_info):
    from ExplanationGenerator.my_types import ReasonType
    from ExplanationGenerator.prompt import KEY_VAR_NON_TERMINAL_AFTER_TERMINAL_PROMPT, KEY_VAR_TERMINAL_PROMPT, KEY_API_INVOKED_PROMPT, KEY_API_EXECUTED_PROMPT, KEY_VAR_MODIFIED_FIELD_PROMPT
    from ExplanationGenerator.explainer import summarize_key_api_effect, _extract_var4_field_and_passed_method, find_terminal_api

    terminal_api = find_terminal_api(report_info["candidates"])
    reason_type = candidate['Reasons'][0]['Explanation Type']
    framework_entry_api = report_info["framework_entry_api"]
    reason_type = candidate['Reasons'][0]['Explanation Type']
    if reason_type == ReasonType.KEY_VAR_TERMINAL.value:
        call_methods = candidate['Reasons'][0]['M_app Trace to Crash API']
        call_chain_to_entry = f"`{call_methods[0]}`"
        for method in call_methods[1:]:
            call_chain_to_entry += f" -> `{method}`"
        terminal_api = call_methods[0]

        reason = KEY_VAR_TERMINAL_PROMPT(framework_entry_api, call_chain_to_entry)
    elif reason_type == ReasonType.KEY_VAR_NON_TERMINAL.value:
        if terminal_api is not None:
            call_methods = candidate['Reasons'][0]['M_app Trace to Crash API']
            call_chain_to_terminal = f"`{call_methods[0]}`"
            for method in call_methods[1:]:
                call_chain_to_terminal += f" -> `{method}`"
                if method == terminal_api:
                    break
            reason = KEY_VAR_NON_TERMINAL_AFTER_TERMINAL_PROMPT(framework_entry_api, terminal_api, call_chain_to_terminal)
        else:
            raise NotImplementedError("Non-terminal key variable explanation is not implemented yet")
    elif reason_type == ReasonType.KEY_API_INVOKED.value:
        key_api = candidate['Reasons'][0]['M_frame Triggered KeyAPI']
        key_field = candidate['Reasons'][0]['M_frame Influenced Field']
        if not key_api in key_api_effects:
            effect = summarize_key_api_effect(candidate, report_info)
            key_api_effects[key_api] = effect
        reason = KEY_API_INVOKED_PROMPT(key_api, key_field, key_api_effects[key_api])
    elif reason_type == ReasonType.KEY_API_EXECUTED.value:
        reason = KEY_API_EXECUTED_PROMPT()
    elif reason_type == ReasonType.KEY_VAR_MODIFIED_FIELD.value:
        _, api, field = _extract_var4_field_and_passed_method(candidate['Reasons'][0]['Explanation Info'])
        reason = KEY_VAR_MODIFIED_FIELD_PROMPT(field, api)
    else:
        raise NotImplementedError(f"Unknown explanation type {reason_type}")

    return reason

def find_explanation(report_name, candidate_name):
    explanation_path = Path(EXPLANATION_RESULT_PATH(report_name)) / "explanation" / "explanation.json"
    explanations = json.load(explanation_path.open())
    for explanation in explanations:
        if explanation["Candidate_Name"] == candidate_name:
            return explanation
    return None

def generate_global_summary(report_info, key_api_effects):
    from ExplanationGenerator.prompt import GLOBAL_SUMMARY_SYSTEM_PROMPT, GLOBAL_SUMMARY_USER_CANDIDATE_PROMPT, GLOBAL_SUMMARY_USER_CRASH_INFORMATION_PROMPT
    from ExplanationGenerator.utils.llm import send_message
    from ExplanationGenerator.utils.parser import parse_message

    report_name = report_info["apk_name"]
    survey_path = FINAL_REPORT_PATH(report_name)
    messages = []
    messages.append({"role": "system", "content": GLOBAL_SUMMARY_SYSTEM_PROMPT})
    crash_prompt = GLOBAL_SUMMARY_USER_CRASH_INFORMATION_PROMPT(report_info["exception_type"], report_info["crash_message"], report_info["stack_trace_short_api"])
    messages.append({"role": "user", "content": crash_prompt})
    for index, candidate in enumerate(report_info["candidates"]):
        candidate_name = candidate["Candidate Name"]
        reason = candidate_reason(candidate, key_api_effects, report_info)
        explanation = find_explanation(report_info["apk_name"], candidate_name)
        candidate_prompt = GLOBAL_SUMMARY_USER_CANDIDATE_PROMPT(index, candidate_name, reason, explanation)
        messages.append({"role": "user", "content": candidate_prompt})
    messages, system_fingerprint = send_message(messages)
    json.dump(messages, open(survey_path / SUMMARY_MESSAGE_FILE_NAME, "w"), indent=4)
    parsed = parse_message(messages[-1]["content"])
    return parsed["Summary"]


def dict2list(d):
    result = []
    def _dict2list(d):
        for k, v in d.items():
            if isinstance(v, dict):
                _dict2list(v)
            elif isinstance(v, list):
                result.extend(v)
            else:
                raise ValueError("Value in dict should be either dict or list")
    
    _dict2list(d)
    return result


def generate(report_name, report_info, key_api_effects):
    survey_path = FINAL_REPORT_PATH(report_name)
    global_summary_path = survey_path / SUMMARY_FILE_NAME
    if global_summary_path.exists():
        logger.info(f"Global summary for {report_name} already exists, skipping")
        return
    
    if len(report_info["candidates"]) == 1:
        logger.info(f"Only one candidate in {report_name}, skipping")
        return

    attempt = 0
    MAX_ATTEMPT = 3
    success = False
    logger.info(f"Generating global summary for {report_name}")
    while attempt < MAX_ATTEMPT:
        try:
            global_summary = generate_global_summary(report_info, key_api_effects)
            success = True
            break
        except KeyError:
            logger.error(f"Error in generating global summary for {report_name}, retrying")
            attempt += 1
    if not success:
        logger.error(f"Failed to generate global summary for {report_name}")
        return
    logger.info(f"Generated global summary for {report_name}, {len(report_info['candidates'])} candidates")
    with open(survey_path / SUMMARY_FILE_NAME, mode='w') as f:
        f.write(global_summary)

def main(report_name):
    # with open(PENDING_SURVEY_PATH, mode='r') as f:
    #     pending_report = json.load(f)
    # pending_list = dict2list(pending_report)

    # logger.info(f"Generating global summary for {len(pending_list)} reports")

    # for report_name in pending_list:
    # for report_dir in FINAL_REPORT_DIRECTORY.iterdir():
        # report_name = report_dir.name
    result_path = Path(EXPLANATION_RESULT_PATH(report_name))
    report_info_path = result_path / "report_info.json"
    report_info = json.load(report_info_path.open())
    key_api_effects_path = result_path / "explanation" / "key_api_effects.json"
    key_api_effects = json.load(key_api_effects_path.open())
    generate(report_name, report_info, key_api_effects)


if __name__ == "__main__":
    main()