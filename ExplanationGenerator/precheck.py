from ExplanationGenerator.config import (
    RunMode,
    PENDING_REPORTS,
    CRASH_REPORT_PATH,
    logger,
    PRE_CHECK_RESULT_PATH,
    RESULT_SUMMARY_PATH
)
from ExplanationGenerator.utils.helper import MethodType, get_method_type
import json
import os
import shutil
from tqdm import tqdm
from tqdm.contrib.logging import logging_redirect_tqdm

class InvalidFrameworkMethodException(Exception):
    pass

class NoFrameworkMethodException(Exception):
    pass

class EmptyExceptionInfoException(Exception):
    pass

class InvalidStateException(Exception):
    pass

class InvalidApplicationMethodException(Exception):
    pass


def report_completion(report):
    """
    Complete the full signature stack trace of report.
    """
    apk_name = report["Crash Info in Dataset"]["Apk name"]
    android_version = report["Fault Localization by CrashTracker"]["Exception Info"]["Target Version of Framework"]
    stack_trace = report["Crash Info in Dataset"]["stack trace signature"]

    def complete_self_invoke_trace(stack_trace, apk_name, android_version):
        stack_trace_reverse = list(reversed(stack_trace))
        for index, (first_sig, second_sig) in enumerate(zip(stack_trace_reverse, stack_trace_reverse[1:])):
            if first_sig != second_sig:
                continue
            if ';' not in first_sig:
                continue
            
            end_index = index + 1
            while end_index < (len(stack_trace_reverse) - 1) and stack_trace_reverse[end_index + 1] == first_sig:
                end_index += 1

            methods = set([s.strip().strip("<>") for s in first_sig.split(";")])
            
            valid_count = 0
            next_method = {}
            for m in methods:
                called_methods = get_called_methods(m, apk_name, android_version)
                if len(called_methods) == 1:
                    called_method = called_methods.pop()
                    if called_method in methods:
                        next_method[m] = called_method
                        valid_count += 1

            if valid_count != len(methods) - 1:
                continue
            invoke_list = []
            for m in methods:
                if m not in set(next_method.values()):
                    invoke_list.append(m)
                    break
            
            while invoke_list[-1] in next_method:
                invoke_list.append(next_method[invoke_list[-1]])
            
            for i in range(end_index, index - 1, -1):
                stack_trace_reverse[i] = invoke_list.pop()

            return list(reversed(stack_trace_reverse))

        return None

    def complete_stack_trace(stack_trace, apk_name, android_version, call_func):
        from ExplanationGenerator.utils.parser import parse_signature, InvalidSignatureException

        for index, (first_sig, second_sig) in enumerate(zip(stack_trace, stack_trace[1:])):
            try:
                parse_signature(first_sig)
            except InvalidSignatureException:
                continue
            if ';' not in second_sig:
                continue

            called_methods = call_func(first_sig, apk_name, android_version)
            second_sig_set = set([s.strip().strip("<>") for s in second_sig.split(";")])
            common_methods = called_methods & second_sig_set
            
            if len(common_methods) == 1:
                stack_trace[index + 1] = common_methods.pop()
                return True
        return False

    from ExplanationGenerator.utils.cg import get_called_methods, get_callers_method

    while True:
        new_trace = complete_self_invoke_trace(stack_trace, apk_name, android_version)
        if new_trace is not None:
            stack_trace = new_trace
            continue
        break

    while True:
        stack_trace_reverse = list(reversed(stack_trace))
        if complete_stack_trace(stack_trace_reverse, apk_name, android_version, get_called_methods):
            stack_trace = list(reversed(stack_trace_reverse))
            continue
        elif complete_stack_trace(stack_trace, apk_name, android_version, get_callers_method):
            continue
        break
        
    report["Crash Info in Dataset"]["stack trace signature"] = stack_trace


def fetch_new_pass_chain(report_name):
    from ExplanationGenerator.config import PASS_CHAIN_REPORT_PATH

    report_path = PASS_CHAIN_REPORT_PATH(report_name)
    with open(report_path, "r") as f:
        new_report = json.load(f)
    pass_chain = new_report["Fault Localization by CrashTracker"]["Exception Info"]["Framework Variable PassChain Info"]
    return pass_chain


def fetch_information(report, write_report=True):
    from ExplanationGenerator.utils.parser import parse_signature, get_framework_method_snippet, get_application_method_snippet, NodeNotFoundException, InvalidSignatureException, MultipleNodeException
    from ExplanationGenerator.config import bypass_signature
    from javalang.parser import JavaSyntaxError

    first_or_none = lambda l: l[0] if len(l) > 0 else None
    bypass_signature_result = lambda method: first_or_none([value for key, value in bypass_signature.items() if method.startswith(key)])

    apk_name = report["Crash Info in Dataset"]["Apk name"]
    if len(report["Fault Localization by CrashTracker"]["Exception Info"]) == 0:
        raise EmptyExceptionInfoException(f"Empty exception info for {apk_name}")
    report_completion(report)
    android_version = report["Fault Localization by CrashTracker"]["Exception Info"][
        "Target Version of Framework"
    ]
    regression_message = report["Fault Localization by CrashTracker"]["Exception Info"]["Regression Message"]
    exception_type = report["Crash Info in Dataset"]["Exception Type"].split(".")[-1]
    if '$' in exception_type:
        exception_type = exception_type.split("$")[-1]
    ets_related_type = report["Fault Localization by CrashTracker"]["Exception Info"]["ETS-related Type"]
    related_variable_type = report["Fault Localization by CrashTracker"]["Exception Info"]["Related Variable Type"]
    related_condition_type = report["Fault Localization by CrashTracker"]["Exception Info"]["Related Condition Type"]
    stack_trace = [method if bypass_signature_result(method) is None else bypass_signature_result(method) for method in report["Crash Info in Dataset"]["stack trace signature"]]
    stack_trace = [method.strip("<>") for method in stack_trace]
    stack_trace_short_api = report["Crash Info in Dataset"]["stack trace"]
    candidates = report["Fault Localization by CrashTracker"]["Buggy Method Candidates"]
    crash_message = report["Crash Info in Dataset"]["Crash Message"]

    framework_trace = []
    framework_short_trace = []
    divider_index = None
    

    for index, (method, method_short_api) in enumerate(zip(stack_trace, stack_trace_short_api)):
        try:
            method_type = get_method_type(method)
            if method_type == MethodType.ANDROID:
                framework_trace.append(method)
                framework_short_trace.append(method_short_api)
                get_framework_method_snippet(method, android_version)
            elif method_type == MethodType.ANDROID_SUPPORT:
                framework_trace.append(method)
                framework_short_trace.append(method_short_api)
                get_application_method_snippet(method, apk_name)
            elif method_type == MethodType.JAVA:
                raise Exception("Java method in framework stack trace")
            elif method_type == MethodType.APPLICATION:
                divider_index = index
                break
        except JavaSyntaxError:
            raise InvalidFrameworkMethodException(
                f"Occur Java syntax error in {method_short_api}, full signature: {method}"
            )
        except InvalidSignatureException:
            raise InvalidFrameworkMethodException(
                f"Invalid signature for {method_short_api}, full signature: {method}"
            )
        except (NodeNotFoundException, FileNotFoundError, MultipleNodeException):
            raise InvalidFrameworkMethodException(
                f"Failed to find framework method snippet for {method_short_api}"
            )
    if len(framework_trace) == 0:
        raise NoFrameworkMethodException("Failed to find any framework method in stack trace")
    
    if divider_index is None:
        raise InvalidStateException("Failed to find divider index for stack trace")

    pending_application_trace = stack_trace[divider_index:]
    pending_application_short_trace = stack_trace_short_api[divider_index:]
    application_trace = []
    application_short_trace = []
    for method, method_short_api in zip(pending_application_trace, pending_application_short_trace):
        try:
            method_type = get_method_type(method)
            if method_type == MethodType.ANDROID:
                break
            elif method_type == MethodType.ANDROID_SUPPORT:
                break
            elif method_type == MethodType.JAVA:
                break
            elif method_type == MethodType.APPLICATION:
                # get_application_method_snippet(method, apk_name)
                application_trace.append(method)
                application_short_trace.append(method_short_api)
        except JavaSyntaxError:
            raise InvalidApplicationMethodException(
                f"Occur Java syntax error in {method_short_api}, full signature: {method}"
            )
        except InvalidSignatureException:
            raise InvalidApplicationMethodException(
                f"Invalid signature for {method_short_api}, full signature: {method}"
            )
        except (NodeNotFoundException, FileNotFoundError, MultipleNodeException):
            raise InvalidApplicationMethodException(
                f"Failed to find application method snippet for {method_short_api}"
            )

    if "Framework Variable PassChain Info" in report["Fault Localization by CrashTracker"]["Exception Info"]:
        try:
            pass_chain = fetch_new_pass_chain(apk_name)
        except KeyError:
            pass_chain = report["Fault Localization by CrashTracker"]["Exception Info"]["Framework Variable PassChain Info"]

        # Check consistency between pass chain and framework trace
        if len(pass_chain) > len(framework_trace):
            msg = f"Length of pass chain {len(pass_chain)} is greater than length of framework trace {len(framework_trace)}"
            logger.error(msg)
            raise InvalidStateException(msg)
        for i in range(len(pass_chain)):
            method_signature, pass_indexes = pass_chain[i].split("@")
            parsed_item_from_pass_chain = parse_signature(method_signature)
            parsed_item_from_framework_trace = parse_signature(framework_trace[i])
            is_matched = True
            for j in range(len(parsed_item_from_pass_chain)):
                if (
                    parsed_item_from_pass_chain[j] is None
                    or parsed_item_from_framework_trace[j] is None
                ):
                    continue
                if parsed_item_from_pass_chain[j] != parsed_item_from_framework_trace[j]:
                    is_matched = False
                    break
            
            if is_matched:
                pass_indexes = json.loads(pass_indexes.strip())
                pass_chain[i] = pass_indexes
            else:
                pass_chain = pass_chain[:i]
                break
    else:
        pass_chain = []
    
    framework_reference_fields = []
    if "Field2InitialMethod" in report["Fault Localization by CrashTracker"]["Exception Info"]:
        framework_reference_fields = [key.strip("<>") for key in report["Fault Localization by CrashTracker"]["Exception Info"]["Field2InitialMethod"]]
    for candidate in report["Fault Localization by CrashTracker"]["Buggy Method Candidates"]:
        for reason in candidate["Reasons"]:
            if "M_frame Influenced Field" in reason:
                for field in reason["M_frame Influenced Field"]:
                    framework_reference_fields.append(field)
    framework_reference_fields = list(set(framework_reference_fields))

    report_info = {
        "apk_name": apk_name,
        "android_version": android_version,
        "regression_message": regression_message,
        "exception_type": exception_type,
        "crash_message": crash_message,
        "stack_trace": stack_trace,
        "stack_trace_short_api": stack_trace_short_api,
        "framework_trace": framework_trace,
        "framework_short_trace": framework_short_trace,
        "application_trace": application_trace,
        "application_short_trace": application_short_trace,
        "framework_pass_chain": pass_chain,
        "framework_entry_api": framework_trace[-1],
        "framework_reference_fields": framework_reference_fields,
        "candidates": candidates,
        "ets_related_type": ets_related_type,
        "related_variable_type": related_variable_type,
        "related_condition_type": related_condition_type,
    }
    if write_report:
        with open(f"{PRE_CHECK_RESULT_PATH(apk_name)}/report_info.json", "w") as f:
            json.dump(report_info, f, indent=4)
    return report_info


def init_report(report_name):
    """
    Initialize report and result directory.

    Load report from directory which specified in configuration and create result directory.

    Copy the report to result directory as well.
    """
    result_dir = PRE_CHECK_RESULT_PATH(report_name)
    os.makedirs(result_dir, exist_ok=True)

    report_path = CRASH_REPORT_PATH(report_name)
    with open(report_path, "r") as f:
        report = json.load(f)
        # Copy report to result directory
        shutil.copy(report_path, f"{result_dir}/{report_name}.json")
    return report, result_dir



def check_snippet(report_info):
    pass



def pre_check(report_name, result_summary):
    """
    Pre-check the report to see if it's valid for further processing.

    If the report is valid, it will create corresponding folder and update the result summary with the statistic information.
    """
    from ExplanationGenerator.config import RUN_MODE

    def statistic(report_info, result_summary):
        # Update statistic information
        sum_ets_related_type = result_summary["statistic"]["ets_related_type"]
        sum_related_variable_type = result_summary["statistic"]["related_variable_type"]
        sum_related_condition_type = result_summary["statistic"]["related_condition_type"]
        candidate_reason_type = result_summary["statistic"]["candidate_reason_type"]

        result_summary["statistic"]["sum"] += 1
        result_summary["statistic"]["sum_candidate"] += len(report_info["candidates"])
        sum_ets_related_type[report_info["ets_related_type"]] = sum_ets_related_type.get(report_info["ets_related_type"], 0) + 1
        sum_related_variable_type[report_info["related_variable_type"]] = sum_related_variable_type.get(report_info["related_variable_type"], 0) + 1
        sum_related_condition_type[report_info["related_condition_type"]] = sum_related_condition_type.get(report_info["related_condition_type"], 0) + 1
        for candidate in report_info["candidates"]:
            reason = candidate["Reasons"][0]["Explanation Type"]
            candidate_reason_type[reason] = candidate_reason_type.get(reason, 0) + 1
            result_summary["pending_list"][report_name]["candidate_reason_types"].append(reason)

    report, result_dir = init_report(report_name)
    try:
        report_info = fetch_information(report)
        result_summary["pending_list"][report_name] = {
            "candidate_reason_types": []
        }
        statistic(report_info, result_summary)
        logger.info(f"Pre-check successfully finished for {report_name}")
    except (InvalidFrameworkMethodException, EmptyExceptionInfoException, InvalidStateException, InvalidApplicationMethodException) as e:
        logger.error(e)
        result_summary["failed_list"][report_name] = {
            "status": "failed",
            "reason": str(e),
        }
        if RUN_MODE == RunMode.ALL:
            shutil.rmtree(result_dir)

def main():
    from ExplanationGenerator.config import RUN_MODE, GPT_MODEL, SEED, TEMPERATURE, CRASH_REPORT_DIRECTORY
    if not os.path.exists(RESULT_SUMMARY_PATH):
        result_summary = {"run mode": RUN_MODE.name, "model": GPT_MODEL, "seed": SEED, "temperature": TEMPERATURE, "statistic":{"sum": 0, "sum_candidate": 0, "ets_related_type": {}, "related_variable_type": {}, "related_condition_type": {}, "candidate_reason_type": {}}, "reports": {}, "pending_list": {}, "failed_list": {}}
    else:
        result_summary = json.loads(open(RESULT_SUMMARY_PATH, 'r').read())

    work_list = []
    if RUN_MODE == RunMode.ALL:
        work_list = os.listdir(CRASH_REPORT_DIRECTORY)
    elif RUN_MODE == RunMode.PENDING:
        work_list = PENDING_REPORTS

    with logging_redirect_tqdm():
        for report_name in tqdm(work_list):
            if any(report_name in collection for collection in [result_summary["pending_list"], result_summary["failed_list"]]):
                continue
            if not os.path.exists(CRASH_REPORT_PATH(report_name)):
                logger.error(f"Report {report_name} not found")
                continue
            pre_check(report_name, result_summary)
            with open(RESULT_SUMMARY_PATH, "w") as f:
                json.dump(result_summary, f, indent=4)

        logger.info(f"Pre-check finished, {len(result_summary['pending_list'])} reports are waiting for explain generation, {len(result_summary['failed_list'])} reports are failed")


if __name__ == "__main__":
    main()
