from panflute import run_filter, Header, Doc, Str, stringify, OrderedList, ListItem, Para, RawBlock, convert_text, BulletList
from pathlib import Path
import json
from copy import deepcopy
import re
import os

FINAL_REPORT_PATH = None
REPORT_INFO_PATH = None
pattern = re.compile(r"\$([A-Za-z_]+)")

def markdown_list(l):
    return OrderedList(*[ListItem(Para(Str(item))) for item in l])

def markdown_bullet_list(l):
    return BulletList(*[ListItem(Para(Str(item))) for item in l])

def sa_explanation(report_name, method_name):
    with open(REPORT_INFO_PATH(report_name), mode="r") as f:
        report_info = json.load(f)
    for candidate in report_info["candidates"]:
        candidate_method_name = candidate["Candidate Name"].split(".")[-1]
        if candidate_method_name == method_name:
            return candidate["Reasons"][0]["Explanation Info"]
    raise ValueError(f"Method {method_name} not found in report {report_name}")


class Report:
    def __init__(self, report_name, report_type) -> None:
        self.report_name = report_name
        with open(REPORT_INFO_PATH(report_name), mode="r") as f:
            self.report_info = json.load(f)
        self.data = {
            "Exception_Type": Para(Str(self.report_info["exception_type"])),
            "Exception_Message": Para(Str(self.report_info["crash_message"])),
            "Stack_Trace": markdown_list(self.report_info["stack_trace_short_api"]),
        }
        survey_path: Path = FINAL_REPORT_PATH(report_name)
        self.candidate_num = sum(os.path.isdir(survey_path / i) for i in os.listdir(survey_path))
        self.ordered_data = {
            "Candidate_Name": [],
            "Method_Code": [],
            "Method_Explanation": []
        }
        for index in range(self.candidate_num):
            self.ordered_data["Candidate_Name"].append(Header(Str(f"Candidate {index + 1}"), level=2))
            with open(FINAL_REPORT_PATH(report_name) / str(index) / "snippet.txt", mode="r") as f:
                snippet = f.read()
            self.ordered_data["Method_Code"].append(RawBlock(f"```java\n{snippet}\n```", format="markdown"))

            if report_type == "llm":
                with open(FINAL_REPORT_PATH(report_name) / str(index) / "explanation.txt", mode="r") as f:
                    explanation = f.read()
            elif report_type == "sa":
                match = re.match("\s*(\S+\s+)*(\S+)\(.*\).*\{", snippet.split("\n")[0])
                if match is None:
                    raise ValueError(f"Method name not found in snippet {snippet}")
                method_name = match.group(2)
                explanation = sa_explanation(report_name, method_name)

            self.ordered_data["Method_Explanation"].append(RawBlock(explanation, format="markdown"))
            
        
    
    def get_data(self, key):
        if key in self.data:
            return self.data[key]
        if key in self.ordered_data:
            return self.ordered_data[key].pop(0)
        return None

def find_candidates(report_info):
    candidates = []
    for candidate in report_info["candidates"]:
        candidate_name = candidate["Candidate Name"]
        candidates.append(candidate_name)
    return markdown_bullet_list(candidates)

def init_doc(doc: Doc):
    report_name = doc.get_metadata("report_name", None)
    if report_name is None:
        raise ValueError("report_name must be provided in metadata")
    report_type = doc.get_metadata("report_type", None)
    if report_type is None or report_type not in ["llm", "sa"]:
        raise ValueError("report_type must be provided and must be either 'llm' or 'sa'")
    report_dir = doc.get_metadata("report_dir", None)
    global FINAL_REPORT_PATH, REPORT_INFO_PATH
    FINAL_REPORT_PATH = lambda name: Path(report_dir) / name
    REPORT_INFO_PATH = lambda name: FINAL_REPORT_PATH(name) / "report_info.json"

    doc.report = Report(report_name, report_type)
    candidate = doc.content[8:]
    for _ in range(doc.report.candidate_num - 1):
        doc.content.extend(deepcopy(candidate))
    global_summary_path: Path = Path("RQ/global_summary") /  report_name / "global_summary.txt"
    c = []
    c.extend([Header(Str("Candidate List"), level=2), find_candidates(doc.report.report_info)])
    if global_summary_path.exists() and report_type == "llm":
        global_summary = global_summary_path.read_text()
        c.append(Header(Str("Global Explanation"), level=2))
        c.extend(convert_text(global_summary))
    doc.content[8:8] = c
    doc.content.append(Header(Str("Note"), level=1))
    doc.content.extend(convert_text(r"The [Codes](./Codes/) folder contains the complete Java code for the relevant candidate methods, the [LLM_Appendix](./LLM_Appendix/) folder contains supplementary information related to the LLM report, and the [StaticAnalysis_Appendix](./StaticAnalysis_Appendix/) folder contains supplementary information related to the static analysis report."))


def end_doc(doc: Doc):
    del doc.report
    del doc.metadata["report_name"]


def replace_data(elem, doc: Doc):
    if type(elem)==Para or type(elem)==Header:
        m = pattern.match(stringify(elem))
        if m:
            key = m.group(1)
            return doc.report.get_data(key)
            

def main(doc=None):
    return run_filter(replace_data, doc=doc, prepare=init_doc, finalize=end_doc)

if __name__ == "__main__":
    main()
