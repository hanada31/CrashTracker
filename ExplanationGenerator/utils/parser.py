import re
import javalang
from ExplanationGenerator.config import logger
from ExplanationGenerator.utils.helper import get_method_type, MethodType

class NodeNotFoundException(Exception):
    pass

class InvalidSignatureException(Exception):
    pass

class MultipleNodeException(Exception):
    pass


def parse_signature(method_signature):
    """
    Example Method Signature:
    1. android.view.ViewRoot: void checkThread()
    2. android.view.ViewRoot.checkThread
    3. android.view.ViewRoot: android.view.ViewParent invalidateChildInParent(int[],android.graphics.Rect)

    Counter Example:
    1. <android.view.View: void invalidate(android.graphics.Rect)>; <android.view.View: void invalidate(int,int,int,int)>; <android.view.View: void invalidate()>
    """
    method_signature = method_signature.strip().strip("<>")
    pattern1 = r"^(\S+)\.(\w+)(\$\S+)?: (\S+) ([\w$]+|<init>)(\([^()]*?\))?$"
    pattern2 = r"^(\S+)\.(\w+)(\$\S+)?\.(\S+)$"
    match1 = re.match(pattern1, method_signature)
    match2 = re.match(pattern2, method_signature)
    if match1:
        (
            package_name,
            class_name,
            inner_class,
            return_type,
            method_name,
            parameter_list,
        ) = match1.groups()
        if inner_class:
            inner_class = inner_class.strip("$")
        if parameter_list:
            parameters = [
                param.strip() for param in parameter_list.strip("()").split(",")
            ]
            # remove empty string
            parameters = list(filter(None, parameters))
        else:
            parameters = None

        return (
            package_name,
            class_name,
            inner_class,
            return_type,
            method_name,
            parameters,
        )
    elif match2:
        package_name, class_name, inner_class, method_name = match2.groups()
        if inner_class:
            inner_class = inner_class.strip("$")
        return package_name, class_name, inner_class, None, method_name, None
    else:
        raise InvalidSignatureException(f"Invalid signature: { method_signature }")


def is_same_signature(signature1, signature2):
    if signature1 == signature2:
        return True
    if signature1.strip().strip("<>") == signature2.strip().strip("<>"):
        return True
    return False


def parse_field_signature(field_signature):
    """
    Example Field Signature:

        1. android.view.ViewRoot: java.lang.Thread mThread
        1. android.view.ViewRoot$InnerClass: java.lang.Thread mThread
    """
    field_signature = field_signature.strip().strip("<>")
    pattern = r"(\S+)\.(\w+)(\$\S+)?: (\S+) (\w+)"
    match = re.match(pattern, field_signature)
    if match:
        package_name, class_name, inner_class, type_name, field_name = match.groups()
        if inner_class:
            inner_class = inner_class.strip("$")
        return package_name, class_name, inner_class, type_name, field_name
    else:
        raise Exception(f"Invalid signature: { field_signature }")


def get_method_start_end(method_node, tree):
    startpos = None
    endpos = None
    startline = None
    endline = None
    for path, node in tree:
        if startpos is not None and method_node not in path:
            endpos = node.position
            endline = node.position.line if node.position is not None else None
            break
        if startpos is None and node == method_node:
            startpos = node.position
            startline = node.position.line if node.position is not None else None
    return startpos, endpos, startline, endline


def get_method_text(
    startpos, endpos, startline, endline, last_endline_index, codelines
):
    if startpos is None:
        return "", None, None, None
    else:
        startline_index = startline - 1
        endline_index = endline - 1 if endpos is not None else None

        # 1. check for and fetch annotations
        if last_endline_index is not None:
            for line in codelines[(last_endline_index + 1) : (startline_index)]:
                if "@" in line:
                    startline_index = startline_index - 1
        meth_text = "<ST>".join(codelines[startline_index:endline_index])
        meth_text = meth_text[: meth_text.rfind("}") + 1]

        # 2. remove trailing rbrace for last methods & any external content/comments
        if not abs(meth_text.count("}") - meth_text.count("{")) == 0:
            # imbalanced braces
            brace_diff = abs(meth_text.count("}") - meth_text.count("{"))

            for _ in range(brace_diff):
                meth_text = meth_text[: meth_text.rfind("}")]
                meth_text = meth_text[: meth_text.rfind("}") + 1]

        meth_lines = meth_text.split("<ST>")
        meth_text = "".join(meth_lines)
        last_endline_index = startline_index + (len(meth_lines) - 1)

        return (
            meth_text,
            (startline_index + 1),
            (last_endline_index + 1),
            last_endline_index,
        )


def parse_code(path):
    with open(path, "r") as f:
        code_lines = f.readlines()
        code_text = "".join(code_lines)

    original_node = javalang.parse.parse(code_text)

    return code_lines, original_node


def parse_code_from_signature(method_signature, apk_name, android_version):
    from ExplanationGenerator.config import ANDROID_CODE_PATH, APK_CODE_PATH
    from ExplanationGenerator.utils.helper import method_signature_into_path

    if get_method_type(method_signature) == MethodType.ANDROID:
        path = f'{ANDROID_CODE_PATH(android_version)}/{method_signature_into_path(method_signature)}'
    elif get_method_type(method_signature) == MethodType.APPLICATION:
        path = f'{APK_CODE_PATH(apk_name)}/{method_signature_into_path(method_signature)}'
    elif get_method_type(method_signature) == MethodType.ANDROID_SUPPORT:
        path = f'{APK_CODE_PATH(apk_name)}/{method_signature_into_path(method_signature)}'
    else:
        raise Exception(f"I don't know where to find the stupid code for {method_signature}")
    
    return parse_code(path)


def get_node(original_node, node_filters, code_lines):
    target_node = original_node
    for node_filter_func in node_filters:
        target_node = node_filter_func(target_node, code_lines)

    return target_node


def first_brace_end_index(code_lines, startline, endline = None):
    stack = []
    for line in range(startline, len(code_lines)):
        str = code_lines[line]
        for char in str:
            if char == '{':
                stack.append(char)
            elif char == '}':
                if len(stack) == 1:
                    if endline is not None and line > endline:
                        raise Exception(f"Longer than expected!")
                    return line
                elif len(stack) == 0:
                    logger.error(f"snippet:\n{''.join(code_lines[startline: line + 1])}")
                    raise Exception(f"Brach match error!")
                else:
                    stack.pop()
    raise Exception(f"Brach match error!")



def reverse_first_brace_end_index(code_lines, endline, startline = None):
    stack = []
    for line in range(endline, -1, -1):
        str = code_lines[line]
        for char in str[::-1]:
            if char == '}':
                stack.append(char)
            elif char == '{':
                if len(stack) > 0:
                    stack.pop()
                else:
                    return line
    raise Exception(f"Brach match error!")


def get_node_snippet(path, node_filters):
    code_lines, original_node = parse_code(path)
    target_node = get_node(original_node, node_filters, code_lines)
    if target_node is None:
        raise NodeNotFoundException(f"Node not found: {node_filters}")

    lex = None
    method_snippet = ""
    if isinstance(target_node, javalang.tree.MethodDeclaration):
        startline = target_node.position.line - 1
        endline = first_brace_end_index(code_lines, startline)
        method_text = "".join(code_lines[startline: endline + 1])
        # startpos, endpos, startline, endline = get_method_start_end(
        #     target_node, original_node
        # )
        # method_text, startline, endline, lex = get_method_text(
        #     startpos, endpos, startline, endline, lex, code_lines
        # )
    elif isinstance(target_node, javalang.tree.ClassDeclaration):
        startline = target_node.position.line - 1
        endline = None
        for node in target_node.fields + target_node.methods:
            if endline is None or node.position.line < endline:
                endline = node.position.line - 1
        for line in range(startline, endline):
            if '{' in code_lines[line]:
                endline = line + 1
        method_text = "".join(code_lines[startline: endline])
    elif isinstance(target_node, javalang.tree.FieldDeclaration):
        startline = target_node.position.line
        endline = startline + 1
        method_text = "".join(code_lines[startline - 1: endline - 1])
    else:
        raise Exception(f"Unsupported node type: {type(target_node)}")
    method_snippet += method_text

    if method_snippet == "":
        return None
    return method_snippet


def method_node_filter_func_generator(method_signature):
    package_name, class_name, inner_class, _, method_name, parameters = parse_signature(
        method_signature
    )
    if inner_class is None:
        innermost_class = class_name
    elif '$' not in inner_class:
        innermost_class = inner_class
    else:
        innermost_class = inner_class.split('$')[-1]

    def node_filter_func(original_tree, code_lines):
        target_class_tree = original_tree
        if not innermost_class.isdigit():
            # Not is a anonymous inner class
            tree_candidate = [
                node
                for _, node in target_class_tree.filter(
                    javalang.tree.ClassDeclaration
                )
                if node.name == innermost_class
            ]
        else:
            # Anonymous inner class
            pending_tree_candidate = [
                node
                for path, node in target_class_tree.filter(
                    javalang.tree.ClassCreator
                )
                if node.body is not None and len(node.body) > 0 and all(isinstance(item, javalang.tree.MethodDeclaration) for item in node.body) and any(item.name == method_name for item in node.body)
            ]
            tree_candidate = []
            for class_creator_node in pending_tree_candidate:
                min_line = class_creator_node.body[0].position.line
                for method_node in class_creator_node.body:
                    if method_node.position.line < min_line:
                        min_line = method_node.position.line
                    
                    # why -2: min_line is real line number, but code_lines is 0-based, so -1; the line before the class line is the line of class declaration so -1 again
                    class_line = reverse_first_brace_end_index(code_lines, min_line - 2)
                    labeled_signature = '.'.join([package_name, class_name, inner_class.replace('$', '.')])
                    if labeled_signature in code_lines[class_line]:
                        tree_candidate.append(class_creator_node)
                
        if len(tree_candidate) == 0:
            return None
        if len(tree_candidate) == 1:
            target_class_tree = tree_candidate[0]
        else:
            raise MultipleNodeException(f"multiple class node: {method_signature}")

        method_nodes = [
            node
            for _, node in target_class_tree.filter(javalang.tree.MethodDeclaration)
        ]
        constructor_nodes = [
            node
            for _, node in target_class_tree.filter(
                javalang.tree.ConstructorDeclaration
            )
        ]
        nodes = method_nodes + constructor_nodes
        target_nodes = []
        for method_node in nodes:
            if method_node.name != method_name:
                continue
            if parameters is not None:
                if len(method_node.parameters) != len(parameters):
                    continue

                unmatched_parameters = False
                for index, value in enumerate(method_node.parameters):
                    if value.type.name != parameters[index].split(".")[-1]:
                        unmatched_parameters = True
                if unmatched_parameters:
                    continue
            target_nodes.append(method_node)

        if len(target_nodes) == 0:
            return None
        elif len(target_nodes) == 1:
            return target_nodes[0]
        else:
            from ExplanationGenerator.config import bypass_signature

            if method_signature in bypass_signature.values():
                return target_nodes[0]
            else:
                raise MultipleNodeException(f"multiple method node: {method_signature}")

    return node_filter_func


def class_node_filter_func_generator(method_signature):
    _, class_name, inner_class, _, _, _ = parse_signature(
        method_signature
    )
    target_class = class_name if inner_class is None else inner_class

    def node_filter_func(original_tree, _):
        tree_candidate = [
            node
            for _, node in original_tree.filter(javalang.tree.ClassDeclaration)
            if node.name == target_class
        ]
        if len(tree_candidate) == 0:
            return None
        if len(tree_candidate) == 1:
            return tree_candidate[0]
        else:
            raise Exception(f"multiple class node: {method_signature}")
    
    return node_filter_func


def field_node_filter_func_generator(field_name):
    def node_filter_func(original_tree, _):
        if isinstance(original_tree, javalang.tree.ClassDeclaration):
            tree_candidate = [
                node
                for node in original_tree.fields
                if node.declarators[0].name == field_name
            ]
        else:
            tree_candidate = [
                node
                for _, node in original_tree.filter(javalang.tree.FieldDeclaration)
                if node.declarators[0].name == field_name
            ]
        if len(tree_candidate) == 0:
            return None
        if len(tree_candidate) == 1:
            return tree_candidate[0]
        else:
            raise Exception(f"multiple field node: {field_name}")

    return node_filter_func


def get_method_snippet(method_signature, apk_name, android_version, framework_reference_fields=[]):
    if get_method_type(method_signature) == MethodType.ANDROID:
        return get_framework_method_snippet(method_signature, android_version, framework_reference_fields)
    elif get_method_type(method_signature) == MethodType.APPLICATION:
        return get_application_method_snippet(method_signature, apk_name)
    elif get_method_type(method_signature) == MethodType.ANDROID_SUPPORT:
        return get_application_method_snippet(method_signature, apk_name)
    else:
        raise Exception(f"Unsupported method type: {method_signature}")


def get_framework_method_snippet(method_signature, android_version, framework_reference_fields=[]):
    from ExplanationGenerator.config import ANDROID_CODE_PATH
    from ExplanationGenerator.utils.helper import method_signature_into_path

    path = f'{ANDROID_CODE_PATH(android_version)}/{method_signature_into_path(method_signature)}'
    class_snippet = get_node_snippet(
        path,
        [class_node_filter_func_generator(method_signature)],
    )
    method_snippet = get_node_snippet(
        path,
        [method_node_filter_func_generator(method_signature)],
    )

    reference_fields = []
    method_package_name, method_class_name, method_inner_class, _, _, _ = parse_signature(method_signature)
    # Find matched field
    for field in framework_reference_fields:
        field_package_name, field_class_name, field_inner_class, field_type_name, field_name = parse_field_signature(field)
        if method_package_name == field_package_name and method_class_name == field_class_name and method_inner_class == field_inner_class:
            reference_fields.append(field_name)
    if len(reference_fields) > 0:
        for field_name in reference_fields:
            field_snippet = get_node_snippet(
                path,
                [class_node_filter_func_generator(method_signature), field_node_filter_func_generator(field_name)],
            )
            class_snippet = f"{class_snippet}\n{field_snippet}"

    snippet = f"{class_snippet}\n{method_snippet}\n\n}}"
    logger.debug(f"Snippet:\n{snippet}")
    return snippet

def get_application_method_snippet(method_signature, apk_name):
    from ExplanationGenerator.config import APK_CODE_PATH
    from ExplanationGenerator.utils.helper import method_signature_into_path

    path = f'{APK_CODE_PATH(apk_name)}/{method_signature_into_path(method_signature)}'
    return get_node_snippet(
        path,
        [method_node_filter_func_generator(method_signature)],
    )


def get_throw_snippet(method_signature, android_version, exception_name):
    def node_filter_func(original_tree, _):
        candidates = [
            node for _, node in original_tree.filter(javalang.tree.ThrowStatement)
        ]
        throw_nodes = []
        for node in candidates:
            if node.expression.type.name == exception_name:
                throw_nodes.append(node)

        if len(throw_nodes) == 0:
            return None
        elif len(throw_nodes) == 1:
            return throw_nodes[0]
        else:
            raise Exception(f"multiple throw node: {method_signature}")

    from ExplanationGenerator.config import ANDROID_CODE_PATH
    from ExplanationGenerator.utils.helper import method_signature_into_path

    path = f'{ANDROID_CODE_PATH(android_version)}/{method_signature_into_path(method_signature)}'
    return get_node_snippet(
        path,
        [method_node_filter_func_generator(method_signature), node_filter_func],
    )


def parse_message(message):
    pattern = r"(\w+):\s```\s(.*?)\s```"
    # 使用 re.DOTALL 使得 '.' 匹配包括换行符在内的所有字符
    matches = re.findall(pattern, message, re.DOTALL)

    extracted = {}
    for label, content in matches:
        extracted[label] = content.strip()

    return extracted


if __name__ == "__main__":
    print(
        parse_signature(
            "android.app.ActivityThread$ApplicationThread$3: void schedulePauseActivity(android.os.IBinder,boolean,boolean,int,boolean)"
        )
    )
    print(
        parse_signature(
            "android.app.ActivityThread$ApplicationThread$3: void schedulePauseActivity"
        )
    )
    print(parse_signature("android.app.ContextImpl.startActivity"))
