# Run Configuration
from enum import Enum
from pathlib import Path


class RunMode(Enum):
    ALL = "all"
    PENDING = "pending"


RUN_MODE = RunMode.ALL
PENDING_REPORTS = ["com.netease.newsreader.activity-481", "com.myung.snsday-45"]

ATTEMPT_TIMES = 3
bypass_signature = {
    # "<android.app.Instrumentation: android.app.Instrumentation$ActivityResult execStartActivity": "android.app.Instrumentation.execStartActivity",
    # "<android.app.Activity: void startActivityForResult": "android.app.Activity.startActivityForResult"
}

# Path Configuration
CRASH_REPORT_DIRECTORY = None
CRASH_REPORT_PATH = None
PASS_CHAIN_REPORT_DIRECTORY = None
PASS_CHAIN_REPORT_PATH = None
ANDROID_CODE_PATH = None
ANDROID_CG_PATH = None
APK_CODE_PATH = None
APK_CG_PATH = None
PRE_CHECK_RESULT_DIRECTORY = None
PRE_CHECK_RESULT_PATH = None
EXPLANATION_RESULT_DIRECTORY = None
EXPLANATION_RESULT_PATH = None
RESULT_SUMMARY_PATH = None
FINAL_REPORT_DIRECTORY = None
FINAL_REPORT_PATH = None

ANDROID_CG_CALLED_CACHE_PATH = None
ANDROID_CG_CALLER_CACHE_PATH = None
APK_CG_CALLED_CACHE_PATH = None
APK_CG_CALLER_CACHE_PATH = None

CACHE_DIRECTORY = None
EXTRACT_CACHE_DIRECTORY = None
EXTRACT_CACHE_CONSTRAINT_PATH = None
EXTRACT_CACHE_MESSAGES_PATH = None

ROOT_PATH = Path(__file__).resolve().parent
REPORT_TEMPLATE_PATH = ROOT_PATH / "report_template.md"

def setup_paths(localization_report_directory, reference_report_directory, reference_files_directory, output_directory):
    from pathlib import Path
    global CRASH_REPORT_DIRECTORY, CRASH_REPORT_PATH, PASS_CHAIN_REPORT_DIRECTORY, PASS_CHAIN_REPORT_PATH, ANDROID_CODE_PATH, ANDROID_CG_PATH, APK_CODE_PATH, APK_CG_PATH, PRE_CHECK_RESULT_DIRECTORY, PRE_CHECK_RESULT_PATH, EXPLANATION_RESULT_DIRECTORY, EXPLANATION_RESULT_PATH, RESULT_SUMMARY_PATH, FINAL_REPORT_PATH, FINAL_REPORT_DIRECTORY
    global ANDROID_CG_CALLED_CACHE_PATH, ANDROID_CG_CALLER_CACHE_PATH, APK_CG_CALLED_CACHE_PATH, APK_CG_CALLER_CACHE_PATH
    global CACHE_DIRECTORY, EXTRACT_CACHE_DIRECTORY, EXTRACT_CACHE_CONSTRAINT_PATH, EXTRACT_CACHE_MESSAGES_PATH
    global REPORT_TEMPLATE_PATH

    localization_report_directory = Path(localization_report_directory)
    reference_report_directory = Path(reference_report_directory)
    reference_files_directory = Path(reference_files_directory)
    output_directory = Path(output_directory)
    ets_default_path = reference_files_directory / "ETS-default"

    CRASH_REPORT_DIRECTORY = localization_report_directory / "output"
    CRASH_REPORT_PATH = lambda name: CRASH_REPORT_DIRECTORY / name / f"{name}.json"
    PASS_CHAIN_REPORT_DIRECTORY = reference_report_directory / "output"
    PASS_CHAIN_REPORT_PATH = lambda name: PASS_CHAIN_REPORT_DIRECTORY / name / f"{name}.json"
    ANDROID_CODE_PATH = lambda v: reference_files_directory / "AndroidCode" / f"android_{v}"
    ANDROID_CG_PATH = lambda v: ets_default_path / f"android{v}" / "CallGraphInfo" / f"android{v}_cg.txt"
    APK_CODE_PATH = lambda apk_name: reference_files_directory / "ApkCode" / apk_name / "sources"
    APK_CG_PATH = lambda apk_name: localization_report_directory / "output" / apk_name / "CallGraphInfo" / f"{apk_name}_cg.txt"

    ANDROID_CG_CALLED_CACHE_PATH = lambda v, hashed_signature: reference_files_directory / "CgCache" / "AndroidCG_called_cache" / f"android_{v}" / f"{hashed_signature}.json"
    ANDROID_CG_CALLER_CACHE_PATH = lambda v, hashed_signature: reference_files_directory / "CgCache" / "AndroidCG_caller_cache" / f"android_{v}" / f"{hashed_signature}.json"
    APK_CG_CALLED_CACHE_PATH = lambda apk_name, hashed_signature: reference_files_directory / "CgCache" / "ApkCG_called_cache" / apk_name / f"{hashed_signature}.json"
    APK_CG_CALLER_CACHE_PATH = lambda apk_name, hashed_signature: reference_files_directory / "CgCache" / "ApkCG_caller_cache" / apk_name / f"{hashed_signature}.json"

    PRE_CHECK_RESULT_DIRECTORY = output_directory / "pre_check"
    PRE_CHECK_RESULT_PATH = lambda name: PRE_CHECK_RESULT_DIRECTORY / name
    EXPLANATION_RESULT_DIRECTORY = output_directory / "explanation"
    EXPLANATION_RESULT_PATH = lambda name: EXPLANATION_RESULT_DIRECTORY / name
    RESULT_SUMMARY_PATH = output_directory / "summary.json"
    FINAL_REPORT_DIRECTORY = output_directory / "reports"
    FINAL_REPORT_PATH = lambda name: FINAL_REPORT_DIRECTORY / name

    CACHE_DIRECTORY = output_directory / "cache"
    EXTRACT_CACHE_DIRECTORY = lambda hash_code: CACHE_DIRECTORY / "extract" / hash_code
    EXTRACT_CACHE_CONSTRAINT_PATH = lambda hash_code: EXTRACT_CACHE_DIRECTORY(hash_code) / "constraint.txt"
    EXTRACT_CACHE_MESSAGES_PATH = lambda hash_code: EXTRACT_CACHE_DIRECTORY(hash_code) / "messages.json"


# LLM Configuration
GPT3_5_TURBO = "gpt-3.5-turbo-1106"
GPT4 = "gpt-4-1106-preview"
OPENAI_API_KEY = None
OPENAI_BASH_URL = None

GPT_MODEL = GPT4
TEMPERATURE = 1
TOKEN_LIMIT = 13000
SEED = 125

def setup_llm(api_key, bash_url=None, model=None, temperature=None, token_limit=None, seed=None):
    global OPENAI_API_KEY
    OPENAI_API_KEY = api_key
    if bash_url:
        global OPENAI_BASH_URL
        OPENAI_BASH_URL = bash_url
    if model:
        global GPT_MODEL
        GPT_MODEL = model
    if temperature:
        global TEMPERATURE
        TEMPERATURE = temperature
    if token_limit:
        global TOKEN_LIMIT
        TOKEN_LIMIT = token_limit

# Logging Configuration
import logging
import sys

logger = logging.getLogger()
streamHandler = logging.StreamHandler(sys.stdout)
streamHandler.setFormatter(logging.Formatter("(%(asctime)s)[%(levelname)s] %(message)s"))
logger.addHandler(streamHandler)
logger.setLevel(logging.INFO)

def add_file_handler(log_file):
    from pathlib import Path
    if not Path(log_file).parent.exists():
        Path(log_file).parent.mkdir(parents=True)
    fileHandler = logging.FileHandler(log_file)
    fileHandler.setFormatter(logging.Formatter("(%(asctime)s)[%(levelname)s] %(message)s"))
    logger.addHandler(fileHandler)
