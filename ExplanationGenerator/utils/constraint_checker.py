import re
from ExplanationGenerator.config import logger
"""
Constraint:
[checkStartActivityResult]: <Parameter 1: Object intent>: The intent parameter should not be null and should have a valid component that can be resolved to an existing activity in the AndroidManifest.xml file.
"""


def parse_constraint(full_constraint, findall=False):
    """
    Example constraint:
    
        1. [foo1]: <Parameter 1: int i> <= 0
        2. [foo2]: <Field ViewRoot: Thread mThread> == null
    """
    pattern = r"\[(.*?)\]: (.*)"
    if not findall:
        match = re.match(pattern, full_constraint)
        if match is None:
            raise ValueError(f"Invalid constraint: {full_constraint}")
        method_name, constraint = match.groups()
    else:
        match = re.findall(pattern, full_constraint)
        if len(match) == 0:
            raise ValueError(f"Invalid constraint: {full_constraint}")
        method_name, constraint = match[0]

    item_pattern = r"<(Parameter|Field) (\w+): (\w+) (\w+)>"
    items = re.findall(item_pattern, constraint)
    # if len(items) == 0:
    #     logger.info(f"No items found in constraint: {full_constraint}")

    for item in items:
        item_type, item_index, item_type_name, item_name = item
        logger.debug(
            f"Parsed Constraint Item: {item_type}, {item_index}, {item_type_name}, {item_name}"
        )
        if item_type == "Parameter":
            if not item_index.isdigit():
                raise ValueError(f"Invalid parameter index: {item_index}")
    return method_name, items


def fill_full_signature(method_signature, constraint):
    pattern = r"\[(.*?)\]: (.*)"
    new_constraint = re.sub(pattern, f"[{method_signature}]: \\2", constraint)

    return new_constraint


def constraint_basic_check(constraint, method_signature, android_version, apk_name, allow_no_item=True, findall=False):
    """
    Only do some basic check, include:

        1. Check the format of the constraint
        2. Check constraint items(Parameters and Fields) are valid(same as the method snippet)
    """
    from ExplanationGenerator.utils.parser import (
        parse_signature,
        parse_code,
        parse_code_from_signature,
        get_node,
        method_node_filter_func_generator,
    )
    from ExplanationGenerator.exceptions import ConstraintBasicCheckError
    _, _, _, _, signature_method_name, _ = parse_signature(method_signature)
    code_lines, original_node = parse_code_from_signature(method_signature, apk_name, android_version)
    target_node = get_node(
        original_node, [method_node_filter_func_generator(method_signature)], code_lines
    )
    parameters = []
    for parameter in target_node.parameters:
        parameters.append((parameter.type.name, parameter.name))

    try:
        constraint_method_name, constraint_items = parse_constraint(constraint, findall=findall)
    except ValueError as e:
        raise ConstraintBasicCheckError(f"Your constraint format is wrong. Please check your constraint follow this format: [method_name]: detailed constraint. example: [foo1]: <Parameter 0: int i> <= 0")
    if constraint_method_name != signature_method_name:
        raise ConstraintBasicCheckError(
            f"The method name in constraint is mismatch: {constraint_method_name} != {signature_method_name}. Please check your constraint."
        )
    
    if not allow_no_item:
        if len(constraint_items) == 0:
            raise ConstraintBasicCheckError("Your constraint does not include any parameters with specified formats, but after being checked by static analysis tools, the constraint is related to some parameters. Please check your constraint follow this format: [method_name]: detailed constraint. example:[foo1]: <Parameter 0: int i> <= 0")

    for item in constraint_items:
        item_type, item_index, item_type_name, item_name = item
        if item_type == "Parameter":
            idx = int(item_index)
            if idx >= len(parameters):
                raise ConstraintBasicCheckError(
                    f"Invalid parameter index: {idx}. The method signature only has {len(parameters)} parameters."
                )
            if item_type_name != parameters[idx][0]:
                raise ConstraintBasicCheckError(
                    f"The type of the parameter {idx} does not match. The type in the constraint is {item_type_name}, but it is {parameters[idx][0]} in the method signature."
                )
            if item_name != parameters[idx][1]:
                raise ConstraintBasicCheckError(
                    f"The name of the parameter {idx} does not match. The name in the constraint is {item_name}, but it is {parameters[idx][1]} in the method signature."
                )
        elif item_type == "Field":
            pass
        else:
            # raise ConstraintBasicCheckError(f"Invalid item type: {constraint}")
            pass


def constraint_static_analysis_check(constraint, method_signature, android_version, pass_chain_indexes, framework_reference_fields, field_check=True):
    """
    Check constraint using the result provided by static analysis.
    """
    from ExplanationGenerator.utils.parser import (
        parse_field_signature,
    )
    from ExplanationGenerator.exceptions import ConstraintStaticAnalysisCheckError

    if field_check:
        field_check_map = {}
        for field in framework_reference_fields:
            _, field_class_name, field_inner_class, field_type_name, field_name = parse_field_signature(field)
            field_check_map[field_name] = {
                "field_class_name": field_class_name.split(".")[-1] if field_inner_class is None else field_inner_class.split(".")[-1],
                "field_type_name": field_type_name.split(".")[-1],
            }

    _, constraint_items = parse_constraint(constraint, findall=True)
    for item in constraint_items:
        item_type, item_index, item_type_name, item_name = item
        if item_type == "Parameter":
            idx = int(item_index)
            if pass_chain_indexes is not None and idx not in pass_chain_indexes:
                raise ConstraintStaticAnalysisCheckError(f"Our static analysis tool identified that parameter {idx} is not related to this constraint, but your constraint include the parameter.")
        elif item_type == "Field":
            field_class_name = item_index
            field_type_name = item_type_name
            field_name = item_name
            if field_check:
                if field_name not in field_check_map:
                    raise ConstraintStaticAnalysisCheckError(f"Field {field_name} not in field check map")
                if item_type_name != field_check_map[field_name]["field_type_name"]:
                    raise ConstraintStaticAnalysisCheckError(
                        f"Field type mismatch: {field_name} != {field_check_map[field_name]['field_type_name']}"
                    )
                if field_class_name != field_check_map[field_name]["field_class_name"]:
                    raise ConstraintStaticAnalysisCheckError(
                        f"Field class mismatch: {field_name} != {field_check_map[field_name]['field_class_name']}"
                    )


if __name__ == "__main__":
    constraint = "[foo1]: <Parameter 0: int i> <= 0"
    method_signature = "android.app.ContextImpl.startActivity"
    android_version = "8.0"
    constraint_basic_check(constraint, method_signature, android_version)
