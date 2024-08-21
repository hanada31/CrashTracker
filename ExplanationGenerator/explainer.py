from ExplanationGenerator.config import logger
from ExplanationGenerator.prompt import EXPLAINER_INIT_PROMPT, EXPLAINER_USER_PROMPT, EXPLAINER_CRASH_PROMPT
from ExplanationGenerator.exceptions import LLMOutputFormatError, TooMuchCandidateError
from ExplanationGenerator.utils.parser import get_application_method_snippet, parse_message, get_framework_method_snippet
from ExplanationGenerator.utils.llm import send_message, write_conversation
import json

def sort_candidates(report_info):
    candidates = report_info["candidates"]
    stack_trace = report_info["stack_trace_short_api"]
    sorted_candidates = []

    for method in stack_trace:
        for candidate in candidates:
            if candidate["Candidate Name"] == method:
                sorted_candidates.append(candidate)
                break
    
    for candidate in candidates:
        if candidate not in sorted_candidates:
            sorted_candidates.append(candidate)
    
    return sorted_candidates


def find_terminal_api(candidates):
    from ExplanationGenerator.my_types import ReasonType

    if len(candidates) <= 1:
        return None
    
    no_need = True
    for candidate in candidates:
        if candidate["Reasons"][0]["Explanation Type"] == ReasonType.KEY_VAR_NON_TERMINAL.value:
            no_need = False
            break

    try:
        for candidate in candidates:
            if candidate["Reasons"][0]["M_app Is Terminate?"]:
                return candidate["Candidate Signature"]
    except KeyError:
        if no_need:
            return None
        else:
            raise NotImplementedError("Cannot find terminal API")
    
    return None


def summarize_key_api_effect(candidate, report_info):
    from ExplanationGenerator.prompt import KEY_API_ROLE_SUMMARIZER_INIT_PROMPT, KEY_API_ROLE_SUMMARIZER_USER_PROMPT

    android_version = report_info["android_version"]
    target_method = candidate["Reasons"][0]["M_frame Triggered KeyAPI"]
    target_field = candidate["Reasons"][0]["M_frame Influenced Field"]
    call_chain = candidate["Reasons"][0]["KeyAPI Invocation Info"][1:-1]
    call_chain_str = " -> ".join(call_chain)
    code_snippets = ""
    for method in call_chain:
        code_snippets += f"[{method}]:\n"
        code_snippets += get_framework_method_snippet(method, android_version)
        code_snippets += "\n\n"

    messages = KEY_API_ROLE_SUMMARIZER_INIT_PROMPT.copy()
    messages.append(
        {
            "role": "user",
            "content": KEY_API_ROLE_SUMMARIZER_USER_PROMPT(target_method, target_field, call_chain_str, code_snippets)
        }
    )
    messages, system_fingerprint = send_message(messages)
    extracted = parse_message(messages[-1]["content"])
    return extracted["Effect"]

def _extract_var4_field_and_passed_method(explanation_info):
    import re
    pattern = "Value of the (\d+) parameter \(start from 0\) in API (\S+) may be wrong and trigger crash\. Method \S+ modify the field variable (\<[\S ]+\>), which may influence the buggy parameter value\."

    m = re.match(pattern, explanation_info)
    if m:
        index, api, field = m.groups()
        return int(index), api, field


def llm_explain(messages, candidate, report_info, terminal_api, key_api_effects):
    from ExplanationGenerator.my_types import ReasonType
    from ExplanationGenerator.prompt import KEY_VAR_NON_TERMINAL_AFTER_TERMINAL_PROMPT, KEY_VAR_TERMINAL_PROMPT, KEY_API_INVOKED_PROMPT, KEY_API_EXECUTED_PROMPT, KEY_VAR_MODIFIED_FIELD_PROMPT

    apk_name = report_info["apk_name"]
    framework_entry_api = report_info["framework_entry_api"]
    method_code = get_application_method_snippet(candidate["Candidate Name"], apk_name)
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
    
    messages.append(
        {
            "role": "user",
            "content": EXPLAINER_USER_PROMPT(method_code, reason)
        }
    )
    messages, system_fingerprint = send_message(messages)
    return messages, terminal_api, key_api_effects


def prepare_init_message(report_info, constraint):
    messages = EXPLAINER_INIT_PROMPT.copy()
    crash_info = {
        "Stack Trace": report_info["stack_trace"],
        "Crash Message": report_info["crash_message"],
        "Android Version": report_info["android_version"],
    }
    crash_info = json.dumps(crash_info, indent=4)
    messages.append(
        {
            "role": "user",
            "content": EXPLAINER_CRASH_PROMPT(crash_info, constraint)
        }
    )
    return messages


def write_explanation(explanations: list, result_dir):
    with open(f"{result_dir}/explanation.txt", "w") as f:
        for explanation in explanations:
            f.write(f"Candidate Name: {explanation['Candidate_Name']}\n\n")
            f.write(f"Analysis: ```\n{explanation['Analysis']}\n```\n\n")
            if "Android_Knowledge" in explanation:
                f.write(f"Android_Knowledge: ```\n{explanation['Android_Knowledge']}\n```\n\n")
            f.write(f"Explanation: ```\n{explanation['Explanation']}\n```\n\n")
            f.write("-----------------------------------\n")


def explain_candidates(report_info, constraint, result_dir):
    import os
    from ExplanationGenerator.utils.parser import NodeNotFoundException, MultipleNodeException
    candidates = sort_candidates(report_info)
    terminal_api = find_terminal_api(candidates)

    def dump_result(messages, key_api_effects, explanations):
        write_conversation(messages, f"{result_dir}/candidate_conversation.txt")
        json.dump(messages, open(f"{result_dir}/candidate_conversation.json", "w"), indent=4)
        json.dump(key_api_effects, open(f"{result_dir}/key_api_effects.json", "w"), indent=4)
        json.dump(explanations, open(f"{result_dir}/explanation.json", "w"), indent=4)
        write_explanation(explanations, result_dir)

    # Cache explanation
    if os.path.exists(f"{result_dir}/candidate_conversation.json"):
        messages = json.load(open(f"{result_dir}/candidate_conversation.json", "r"))
        key_api_effects = json.load(open(f"{result_dir}/key_api_effects.json", "r"))
        explanations = json.load(open(f"{result_dir}/explanation.json", "r"))
        candidates_len = len(candidates)
        messages_len = (len(messages) - 2) / 2
        if candidates_len == messages_len:
            return
        candidates_new = []
        for candidate in candidates:
            if candidate["Candidate Name"] not in [explanation["Candidate_Name"] for explanation in explanations]:
                candidates_new.append(candidate)
        candidates = candidates_new
    else:
        messages = prepare_init_message(report_info, constraint)
        explanations = []
        key_api_effects = {}
    if len(candidates) > 6:
        raise TooMuchCandidateError("Too much candidates to explain")
    for index, candidate in enumerate(candidates):
        logger.info(f"Explaining {candidate['Candidate Name']}, candidate: {index + 1}/{len(candidates)}")
        attempt = 0
        MAX_ATTEMPT = 3

        while attempt < MAX_ATTEMPT:
            try:
                messages, _, key_api_effects = llm_explain(messages, candidate, report_info, terminal_api, key_api_effects)

                parsed = parse_message(messages[-1]["content"])
                if "Analysis" not in parsed or "Explanation" not in parsed:
                    raise LLMOutputFormatError("Invalid output format from LLM")
                explanation = {
                    "Candidate_Name": candidate["Candidate Name"],
                    "Analysis": parsed["Analysis"],
                    "Explanation": parsed["Explanation"],
                }
                if "Android_Knowledge" in parsed:
                    explanation["Android_Knowledge"] = parsed["Android_Knowledge"]
                logger.info(f"Explanation for {candidate['Candidate Name']} is generated!")
            except NodeNotFoundException as e:
                logger.error(f"Failed to generate explanation for {candidate['Candidate Name']}: {e}")
                explanation = {
                    "Candidate_Name": candidate["Candidate Name"],
                    "Analysis": "Method not found",
                    "Explanation": "Method not found",
                }
                break
            except MultipleNodeException as e:
                logger.error(f"Failed to generate explanation for {candidate['Candidate Name']}: {e}")
                explanation = {
                    "Candidate_Name": candidate["Candidate Name"],
                    "Analysis": "Multiple nodes found",
                    "Explanation": "Multiple nodes found",
                }
                break
            except FileNotFoundError as e:
                logger.error(f"Failed to generate explanation for {candidate['Candidate Name']}: {e}")
                explanation = {
                    "Candidate_Name": candidate["Candidate Name"],
                    "Analysis": "File not found",
                    "Explanation": "File not found",
                }
                break
            except LLMOutputFormatError as e:
                logger.error(f"Failed to generate explanation for {candidate['Candidate Name']}: {e}")
                explanation = {
                    "Candidate_Name": candidate["Candidate Name"],
                    "Analysis": "Invalid output format from LLM",
                    "Explanation": "Invalid output format from LLM",
                }
                attempt += 1
                logger.error(f"Retrying... {attempt}/{MAX_ATTEMPT}")
            else:
                break
        
        explanations.append(explanation)
        dump_result(messages, key_api_effects, explanations)
    
    dump_result(messages, key_api_effects, explanations)

    return
    