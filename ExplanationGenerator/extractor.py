from ExplanationGenerator.config import logger
from ExplanationGenerator.prompt import (
    EXTRACTOR_INIT_PROMPT,
    EXTRACTOR_USER_PROMPT,
    INFERRER_INIT_PROMPT,
    INFERRER_USER_PROMPT,
)
from ExplanationGenerator.exceptions import LLMOutputFormatError, ConstraintBasicCheckError, ConstraintStaticAnalysisCheckError, ConstraintCheckError
from ExplanationGenerator.utils.parser import get_framework_method_snippet, get_method_snippet
from ExplanationGenerator.utils.llm import send_message, pretty_log_conversation, save_messages
from ExplanationGenerator.utils.parser import parse_message
import os
import json


def extracted_message_hash_helper(messages, regression_message):
    import re

    replaced_messages = messages.copy()
    last_message = replaced_messages[-1]["content"]
    safe_regression_message = re.escape(regression_message)
    pattern = r"Exception Message: .*\s```"
    last_message = re.sub(pattern, f"Exception Message: {safe_regression_message}\n```", last_message)
    replaced_messages[-1] = {
        "role": messages[-1]["role"],
        "content": last_message
    }

    return replaced_messages


def extract_constraint(apk_name, method_signature, android_version, exception_name, crash_message, framework_reference_fields, regression_message, allow_cache=True, init_prompt=None, check=True):
    code_snippet = get_method_snippet(method_signature, apk_name, android_version, framework_reference_fields)
    logger.debug(f"code_snippet for {method_signature}:\n {code_snippet}")

    if init_prompt is None:
        messages = EXTRACTOR_INIT_PROMPT.copy()
    else:
        messages = init_prompt.copy()
    messages.append(
        {"role": "user", "content": EXTRACTOR_USER_PROMPT(code_snippet, exception_name, crash_message)}
    )

    # Check if these is cache
    import json
    import hashlib
    from ExplanationGenerator.config import EXTRACT_CACHE_DIRECTORY, EXTRACT_CACHE_CONSTRAINT_PATH, EXTRACT_CACHE_MESSAGES_PATH

    hit_cache = False
    query_messages = extracted_message_hash_helper(messages, regression_message)
    query_str = json.dumps([json.dumps(x, sort_keys=True) for x in query_messages])
    query_hash = hashlib.sha256(query_str.encode()).hexdigest()
    if os.path.exists(EXTRACT_CACHE_DIRECTORY(query_hash)) and allow_cache:
        result_messages = json.load(open(EXTRACT_CACHE_MESSAGES_PATH(query_hash), "r"))
        constraint = open(EXTRACT_CACHE_CONSTRAINT_PATH(query_hash), "r").read()
        hit_cache = True
        logger.info(f"Hit cache for query {query_hash}")
    else:
        result_messages, system_fingerprint = send_message(messages, json_mode=False)
        pretty_log_conversation(result_messages)
        if check:
            extracted = parse_message(result_messages[-1]["content"])
            if "Constraint" not in extracted or "Analysis" not in extracted:
                raise LLMOutputFormatError("Invalid output format from LLM")
            constraint = extracted["Constraint"]
        else:
            constraint = result_messages[-1]["content"]
    return constraint, result_messages, hit_cache


def infer_constraint(
    inferred_method_signature,
    android_version,
    original_constraint,
    framework_reference_fields,
    received_messages,
    apk_name
):
    code_snippet = get_method_snippet(inferred_method_signature, apk_name, android_version, framework_reference_fields)

    messages = received_messages.copy()
    messages.append(
        {
            "role": "user",
            "content": INFERRER_USER_PROMPT(code_snippet, original_constraint),
        }
    )

    # Check if these is cache
    import json
    import hashlib
    from ExplanationGenerator.config import EXTRACT_CACHE_DIRECTORY, EXTRACT_CACHE_CONSTRAINT_PATH, EXTRACT_CACHE_MESSAGES_PATH

    hit_cache = False
    query_messages = messages.copy()
    query_str = json.dumps([json.dumps(x, sort_keys=True) for x in query_messages])
    query_hash = hashlib.sha256(query_str.encode()).hexdigest()

    if os.path.exists(EXTRACT_CACHE_DIRECTORY(query_hash)):
        result_messages = json.load(open(EXTRACT_CACHE_MESSAGES_PATH(query_hash), "r"))
        constraint = open(EXTRACT_CACHE_CONSTRAINT_PATH(query_hash), "r").read()
        hit_cache = True
        logger.info(f"Hit cache for query {query_hash}")
    else:
        result_messages, system_fingerprint = send_message(messages, json_mode=False)
        pretty_log_conversation(result_messages)
        extracted = parse_message(result_messages[-1]["content"])
        if "Constraint" not in extracted or "Analysis" not in extracted:
            raise LLMOutputFormatError("Invalid output format from LLM")
        constraint = extracted["Constraint"]
    return constraint, result_messages, hit_cache


def query_framework_constraint(report_info, result_path):
    def save_constraint(
        constraint, extract_constraint_messages, infer_constraint_messages
    ):
        with open(f"{result_path}/constraint.txt", "w") as f:
            f.write(constraint)
        save_messages(
            extract_constraint_messages,
            result_path,
            "extract_conversation",
        )
        save_messages(
            infer_constraint_messages,
            result_path,
            "infer_conversation",
        )
    
    def cache_extract_constraint(constraint, extract_message, regression_message):
        from ExplanationGenerator.config import EXTRACT_CACHE_DIRECTORY, EXTRACT_CACHE_CONSTRAINT_PATH
        import hashlib
        import json

        query_messages = extracted_message_hash_helper(extract_message[:-1], regression_message)
        query_str = json.dumps([json.dumps(x, sort_keys=True) for x in query_messages])
        query_hash = hashlib.sha256(query_str.encode()).hexdigest()

        if not os.path.exists(EXTRACT_CACHE_DIRECTORY(query_hash)):
            os.makedirs(EXTRACT_CACHE_DIRECTORY(query_hash), exist_ok=True)
            with open(EXTRACT_CACHE_CONSTRAINT_PATH(query_hash), "w") as f:
                f.write(constraint)
            save_messages(extract_message, EXTRACT_CACHE_DIRECTORY(query_hash), "messages")
            logger.info(f"Cache extracted constraint for query {query_hash}")
    
    def cache_infer_constraint(constraint, infer_messages):
        from ExplanationGenerator.config import EXTRACT_CACHE_DIRECTORY, EXTRACT_CACHE_CONSTRAINT_PATH
        import hashlib
        import json

        query_messages = infer_messages[:-1].copy()
        query_str = json.dumps([json.dumps(x, sort_keys=True) for x in query_messages])
        query_hash = hashlib.sha256(query_str.encode()).hexdigest()

        if not os.path.exists(EXTRACT_CACHE_DIRECTORY(query_hash)):
            os.makedirs(EXTRACT_CACHE_DIRECTORY(query_hash), exist_ok=True)
            with open(EXTRACT_CACHE_CONSTRAINT_PATH(query_hash), "w") as f:
                f.write(constraint)
            save_messages(infer_messages, EXTRACT_CACHE_DIRECTORY(query_hash), "messages")
            logger.info(f"Cache inferred constraint for query {query_hash}")
    
    # Cache constraint
    if os.path.exists(f"{result_path}/constraint.txt"):
        with open(f"{result_path}/constraint.txt", "r") as f:
            constraint = f.read()
        query_count = len(report_info["framework_trace"])
        return constraint, 0, query_count, query_count

    from ExplanationGenerator.extractor import extract_constraint, infer_constraint
    from ExplanationGenerator.utils.constraint_checker import fill_full_signature, parse_constraint, constraint_basic_check, constraint_static_analysis_check
    from ExplanationGenerator.config import ATTEMPT_TIMES
    from itertools import zip_longest

    apk_name = report_info["apk_name"]
    framework_trace = report_info["framework_trace"]
    android_version = report_info["android_version"]
    exception_type = report_info["exception_type"]
    crash_message = report_info["crash_message"]
    framework_pass_chain = report_info["framework_pass_chain"]
    framework_reference_fields = report_info["framework_reference_fields"]
    regression_message = report_info["regression_message"]
    bypass_count = 0
    cache_hit_count = 0

    first = True
    infer_messages = INFERRER_INIT_PROMPT.copy()
    for index, (method_signature, pass_chain_indexes) in enumerate(zip_longest(
        framework_trace, framework_pass_chain
    )):
        logger.info(f"Querying constraint of {method_signature}, framework trace: {index + 1}/{len(framework_trace)}")
        attempts = 0
        successful = False
        constraints = []
        messages = []
        while attempts < ATTEMPT_TIMES:
            try:
                if first:
                    constraint_unchecked, message_unchecked, hit_cache = extract_constraint(apk_name, method_signature, android_version, exception_type, crash_message, framework_reference_fields, regression_message
                    )
                else:
                    constraint_unchecked, message_unchecked, hit_cache = infer_constraint(
                        method_signature,
                        android_version,
                        constraint,
                        framework_reference_fields,
                        infer_messages,
                        apk_name
                    )
                # TODO: Check system_fingerprint
                constraint_basic_check(constraint_unchecked, method_signature, android_version, apk_name)
                constraints.append(constraint_unchecked)
                messages.append(message_unchecked)
                if hit_cache is False:
                    if (pass_chain_indexes is not None and len(pass_chain_indexes) > 0) or len(framework_reference_fields) > 0:
                        constraint_static_analysis_check(
                            constraint_unchecked, method_signature, android_version, pass_chain_indexes, framework_reference_fields
                        )
                elif hit_cache is True:
                    cache_hit_count += 1
                    logger.info("Hit cache, skip static analysis check")
                else:
                    raise ValueError("What happened?")
            except LLMOutputFormatError as e:
                attempts += 1
                logger.error(f"LLM output format error: {e}")
                continue
            except ConstraintBasicCheckError as e:
                attempts += 1
                logger.error(f"Constraint basic check failed: {e}, attempts: {attempts}/{ATTEMPT_TIMES}, constraint: {constraint_unchecked}")
                continue
            except ConstraintStaticAnalysisCheckError as e:
                attempts += 1
                logger.error(f"Constraint static analysis check failed: {e}, attempts: {attempts}/{ATTEMPT_TIMES}, constraint: {constraint_unchecked}, pass_chain_indexes: {pass_chain_indexes}")
                continue
            else:
                successful = True
                break
        
        constraint_idx = -1
        if not successful:
            if len(constraints) == 0:
                raise ConstraintCheckError(f"No constraint pass basic check after {ATTEMPT_TIMES} attempts in {apk_name}")
            # If all attempts failed, try to check all constraint is the same
            constraint_items_list = []
            for constraint in constraints:
                _, constraint_items = parse_constraint(constraint)
                constraint_items_list.append(set(constraint_items))
            longest_constraint_items = constraint_items_list[0]
            longest_index = 0
            for index, constraint_items in enumerate(constraint_items_list):
                if len(constraint_items) > len(longest_constraint_items):
                    longest_constraint_items = constraint_items
                    longest_index = index
            for constraint_items in constraint_items_list:
                if constraint_items & longest_constraint_items != longest_constraint_items:
                    failed_path = f"Meta/Results/failed_list/{apk_name}"
                    os.makedirs(failed_path, exist_ok=True)
                    with open(f"{failed_path}/constraints.json", "w") as f:
                        json.dump(constraints, f, indent=4)
                    raise ConstraintCheckError(f"Cannot pass constraint consistent check for {len(constraints)} constraints after {ATTEMPT_TIMES} attempts in {apk_name}")
            bypass_count += 1
            constraint_idx = longest_index
            logger.info("All attempts failed, but constraints are consistent, bypassing")

        msg = messages[constraint_idx]
        constraint = constraints[constraint_idx]
        if not first:
            infer_messages = msg
            cache_infer_constraint(constraint, infer_messages)
        else:
            extract_message = msg
            cache_extract_constraint(constraint, extract_message, regression_message)
        first = False
    constraint = fill_full_signature(framework_trace[-1], constraint)
    save_constraint(constraint, extract_message, infer_messages)

    query_count = len(framework_trace)
    return constraint, bypass_count, query_count, cache_hit_count


if __name__ == "__main__":
    extract_constraint(
        "android.app.ContextImpl.startActivity", 2.3, "AndroidRuntimeException"
    )
