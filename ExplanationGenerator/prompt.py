EXTRACTOR_SYSTEM_PROMPT = """
Your task is to extract the precondition constraint of the target exception in Java methods and convert them into constraint related to method parameters or class field.

Following are rules for formatting the constraints, you should replace the content in the brackets with the corresponding information:

You should describe parameter using this format: <Parameter {0-based index of parameter}: {type of parameter} {parameter name}>
Describe class field in this format: <Field {class name}: {type of field} {field name}> 
Describe constraint in this format: [{Constrained method name}]: {Constraint}

// Note: If the variable is a parameter of the method provided in `Code` or a field of the class to which it belongs, it must follow the specified format.
// Note: We will use static analysis tool to check the result, so the parameter type must match the method signature, and the field type must match its declaration.
// Note: You cannot use this format for other content(like local variable, method etc.)

Please use the following format for the conversation:
Code: ```
Java method code

// Note: The class code is incomplete, other methods which is not related to this method will be omitted.
```

Exception: ```
Target exception type name and a possible exception message

// Note: The crash message is only used to determine which target exception it is when there are multiple exceptions of the same type, for example.
// Do not attempt to resolve exceptions that trigger the same crash message alone.
```

Analysis: ```
Your detailed analysis process, should be step by step.

// You can start at control flow analysis
```

Constraint: ```
Constraint related to method parameters. The method will throw **target exception** when this constraint is met.

// Note: The constraint should not include other method, You should describe the specific effects of other methods in the constraints.
// Note: The content of this section is the final result; this section should be independent, and you cannot reference content from other sections.
// Note: Constraints should only be extracted using information from the current method snippet, without incorporating your Android knowledge. Constraints should be as formalized as possible.
```
"""

EXTRACTOR_USER_EXAMPLE1 = """
Code: ```
public class SELinux {

    public static boolean restorecon(String pathname) throws NullPointerException {
        if (pathname == null) { throw new NullPointerException("pathname is null"); }
        return native_restorecon(pathname, 0);
    }
}
```

Exception: ```
Exception Type: NullPointerException
Exception Message: pathname is null
```
"""

EXTRACTOR_ASSISTANT_EXAMPLE1 = """
Analysis: ```
The provided Java method 'restorecon' from the class 'SELinux' throws a 'NullPointerException' if a specific condition is met. The condition for throwing the exception is checked at the beginning of the method.

Here is the step-by-step analysis of the control flow:

1. The method 'restorecon' takes a single parameter 'pathname' of type 'String'.

2. The first operation in the method is a conditional check to see if 'pathname' is null.

3. If 'pathname' is found to be null, the method explicitly throws a 'NullPointerException'.

4. There is no message provided with the thrown 'NullPointerException', as the exception is thrown using the default constructor.

From this analysis, we can deduce that the precondition constraint for throwing a 'NullPointerException' is that the 'pathname' parameter must be null.
```

Constraint: ```
[restorecon]: <Parameter 0: String pathname> is null.
```
"""

EXTRACTOR_USER_EXAMPLE2 = """
Code: ```
public final class Pools {

        public boolean release(T instance) {
            if (isInPool(instance)) {
                throw new IllegalStateException("Already in the pool!");
            }
            if (mPoolSize < mPool.length) {
                mPool[mPoolSize] = instance;
                mPoolSize++;
                return true;
            }
            return false;
        }
}
```

Exception: ```
Exception Type: IllegalStateException
Exception Message: Already in the pool!
```
"""

EXTRACTOR_ASSISTANT_EXAMPLE2 = """
Analysis: ```
The provided Java method 'release' from the final class 'Pools' throws an 'IllegalStateException' if the object 'instance' is already in the pool. To determine the precondition constraint for the target exception, we need to analyze the control flow that leads to the exception.

Here is the step-by-step analysis:

1. The method 'release' takes a single parameter 'instance' of generic type 'T'.

2. The first operation in the method is a call to the method 'isInPool(instance)' which checks if the 'instance' is already in the pool.

3. If 'isInPool(instance)' returns true, meaning the 'instance' is already in the pool, the method throws an 'IllegalStateException' with the message "Already in the pool!".

4. There is no other information provided about the 'isInPool' method within the given code snippet. However, based on the exception message, we can infer that the 'isInPool' method is used to check for the presence of the 'instance' in the pool.

From this analysis, we can deduce that the precondition constraint for throwing an 'IllegalStateException' with the message "Already in the pool!" is that the method 'isInPool' must return true for the given 'instance'.
```

Constraint: ```
[release]: isInPool(<Parameter 0: T instance>) returns true
```
"""

EXTRACTOR_USER_EXAMPLE3 = """
Code: ```
public class Instrumentation {
    /*package*/ static void checkStartActivityResult(int res, Object intent) {
        if (res >= ActivityManager.START_SUCCESS) {
            return;
        }
        
        switch (res) {
            case ActivityManager.START_INTENT_NOT_RESOLVED:
            case ActivityManager.START_CLASS_NOT_FOUND:
                if (intent instanceof Intent && ((Intent)intent).getComponent() != null)
                    throw new ActivityNotFoundException(
                            "Unable to find explicit activity class "
                            + ((Intent)intent).getComponent().toShortString()
                            + "; have you declared this activity in your AndroidManifest.xml?");
                throw new ActivityNotFoundException(
                        "No Activity found to handle " + intent);
            case ActivityManager.START_PERMISSION_DENIED:
                throw new SecurityException("Not allowed to start activity "
                        + intent);
            case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
                throw new AndroidRuntimeException(
                        "FORWARD_RESULT_FLAG used while also requesting a result");
            case ActivityManager.START_NOT_ACTIVITY:
                throw new IllegalArgumentException(
                        "PendingIntent is not an activity");
            default:
                throw new AndroidRuntimeException("Unknown error code "
                        + res + " when starting " + intent);
        }
    }
}
```

Exception: ```
Exception Type: ActivityNotFoundException
Exception Message: No Activity found to handle Intent { act=android.intent.action.VIEW dat=market://details?id=com.xtreme_.hairstyleswiththeirhands }
```
"""

EXTRACTOR_ASSISTANT_EXAMPLE3 = """
Analysis: ```
The provided Java method 'checkStartActivityResult' from the class 'Instrumentation' throws an 'ActivityNotFoundException' under certain conditions. To determine the precondition constraint for the target exception, we need to analyze the control flow that leads to the exception.

The target exception 'ActivityNotFoundException' with the message "No Activity found to handle {intent}" is thrown in the following case:

1. The method checks if the 'res' parameter is less than 'ActivityManager.START_SUCCESS'. If 'res' is greater than or equal to 'ActivityManager.START_SUCCESS', the method returns early and no exception is thrown.

2. If the 'res' parameter is less than 'ActivityManager.START_SUCCESS', the method enters a switch statement based on the value of 'res'.

3. The specific case for the target exception message is 'ActivityManager.START_INTENT_NOT_RESOLVED' or 'ActivityManager.START_CLASS_NOT_FOUND'. If the 'res' matches one of these cases, the method further checks if 'intent' is an instance of 'Intent' and if it has a non-null component via 'getComponent()'.

4. If 'intent' is not an instance of 'Intent' or 'getComponent()' returns null, the 'ActivityNotFoundException' is thrown with the message "No Activity found to handle {intent}". This is the case that matches our target exception message.

Therefore, the precondition constraint for the target exception is that 'res' must be one of the specific error codes ('ActivityManager.START_INTENT_NOT_RESOLVED' or 'ActivityManager.START_CLASS_NOT_FOUND') and 'intent' should not be an instance of 'Intent' or 'intent' as an 'Intent' should have a null component.
```

Constraint: ```
[checkStartActivityResult]: <Parameter 0: int res> is either ActivityManager.START_INTENT_NOT_RESOLVED or ActivityManager.START_CLASS_NOT_FOUND, and (<Parameter 1: Object intent> is not an instance of Intent or <Parameter 1: Object intent> as Intent has a null getComponent() result).
```
"""

EXTRACTOR_INIT_PROMPT = []
EXTRACTOR_INIT_PROMPT.append({"role": "system", "content": EXTRACTOR_SYSTEM_PROMPT})
EXTRACTOR_INIT_PROMPT.append({"role": "user", "content": EXTRACTOR_USER_EXAMPLE1})
EXTRACTOR_INIT_PROMPT.append({"role": "assistant", "content": EXTRACTOR_ASSISTANT_EXAMPLE1})
EXTRACTOR_INIT_PROMPT.append({"role": "user", "content": EXTRACTOR_USER_EXAMPLE2})
EXTRACTOR_INIT_PROMPT.append({"role": "assistant", "content": EXTRACTOR_ASSISTANT_EXAMPLE2})
EXTRACTOR_INIT_PROMPT.append({"role": "user", "content": EXTRACTOR_USER_EXAMPLE3})
EXTRACTOR_INIT_PROMPT.append({"role": "assistant", "content": EXTRACTOR_ASSISTANT_EXAMPLE3})

EXTRACTOR_USER_PROMPT = (
    lambda code, exception_name, crash_message: f"""
Code: ```
{code}
```

Exception: ```
Exception Name: {exception_name}
Exception Message: {crash_message}
```
"""
)

INFERRER_SYSTEM_PROMPT = """
You are an Android expert that assist with inferring the triggering constraint of the target exception in Java methods

We will provide you with the Java method code which may trigger an exception. We will also provide a constraint of method which is invoked in code. A exception will be triggered when this constraint is met.

Your task is to convert the constraint related with original method into constraint related to current method parameters and class field.

All the code comes from the Android framework, for methods we have not provided, you can assume they are the Android framework methods that you are familiar with.

Following are rules for formatting the constraints, you should replace the content in the brackets with the corresponding information:

You should describe parameter using this format: <Parameter {0-based index of parameter}: {type of parameter} {parameter name}>
Describe class field in this format: <Field {class name}: {type of field} {field name}> 
Describe constraint in this format: [{Constrained method name}]: {Constraint}

// Note: If the variable is a parameter of the method provided in `Code` or a field of the class to which it belongs, it must follow the specified format.
// Note: We will use static analysis tool to check the result, so the parameter type must match the method signature, and the field type must match its declaration.
// Note: You cannot use this format for other content(like local variable, method etc.)

Please answer in the following format:
Code: ```
Java method code
```

Original_Constraint: ```
The constrained method and constraint content. A exception will be triggered when this constraint is met.
```

Analysis: ```
Your detailed analysis process, should be step by step.

// You can start at control flow analysis
```

Constraint: ```
Conditions related to current method parameters or class field. Original constraint will be met if this condition is met.

// Note: The constraint should not include other method, You should describe the specific effects of other methods in the constraints.
// Note: The content of this section is the final result; this section should be independent, and you cannot reference content from other sections.
``` 
"""

INFERRER_USER_EXAMPLE1 = """
Code: ```
public abstract class AbstractWindowedCursor extends AbstractCursor {

    protected void checkPosition() {
        super.checkPosition();
        
        if (mWindow == null) {
            throw new StaleDataException("Attempting to access a closed CursorWindow." +
                    "Most probable cause: cursor is deactivated prior to calling this method.");
        }
    }


}
```

Original_Constraint: ```
[checkPosition]: <Field AbstractCursor: int mPos> == -1
```
"""

INFERRER_ASSISTANT_EXAMPLE1 = """
Analysis: ```
The method `checkPosition` in the `AbstractWindowedCursor` class calls `super.checkPosition()`, which is a method from its superclass `AbstractCursor`. 

The constraint on `super.checkPosition()` states that an exception will be triggered if the field `mPos` in the `AbstractCursor` class is equal to -1.

In the context of `checkPosition` in the `AbstractWindowedCursor` class, there is no change to the value of `mPos` before or after the call to `super.checkPosition()`. Therefore, the constraint from `super.checkPosition()` applies directly to the `checkPosition` method in the `AbstractWindowedCursor` class without any modification.

Since `mWindow` is checked for null after the call to `super.checkPosition()`, it does not affect the constraint related to `mPos`.
```

Constraint: ```
[checkPosition]: <Field AbstractCursor: int mPos> == -1
```
"""

INFERRER_USER_EXAMPLE2 = """
Code: ```
public class Resources {

    public String getString(int id) throws NotFoundException {
        CharSequence res = getText(id);
        if (res != null) {
            return res.toString();
        }
        throw new NotFoundException("String resource ID #0x"
                                    + Integer.toHexString(id));
    }

}
```

Original_Constraint: ```
[getText]: <Parameter 0: int id> correspond to a invalid resource ID that not exists in <Field Resources: AssetManager mAssets>.
```
"""

INFERRER_ASSISTANT_EXAMPLE2 = """
Analysis: ```
The method `getString` in the `Resources` class calls the method `getText` with the parameter `id`.

The constraint on `getText` states that an exception will be triggered if the parameter `id` corresponds to an invalid resource ID that does not exist in the `AssetManager` instance referenced by the field `mAssets` in the `Resources` class.

In the context of `getString`, the variable `id` passed to `getText` is the same `id` received by `getString`. There are no transformations or conditions applied to `id` before calling `getText`.

Therefore, the constraint from `getText` can be directly translated to the parameters of `getString` with respect to the `id` variable.
```

Constraint: ```
[getString]: <Parameter 0: int id> corresponds to an invalid resource ID that does not exist in <Field Resources: AssetManager mAssets>.
```
"""

INFERRER_USER_EXAMPLE3 = """
Code: ```
public class Instrumentation {

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        IApplicationThread whoThread = (IApplicationThread) contextThread;
        if (mActivityMonitors != null) {
            synchronized (mSync) {
                final int N = mActivityMonitors.size();
                for (int i=0; i<N; i++) {
                    final ActivityMonitor am = mActivityMonitors.get(i);
                    if (am.match(who, null, intent)) {
                        am.mHits++;
                        if (am.isBlocking()) {
                            return requestCode >= 0 ? am.getResult() : null;
                        }
                        break;
                    }
                }
            }
        }
        try {
            intent.migrateExtraStreamToClipData();
            intent.prepareToLeaveProcess();
            int result = ActivityManagerNative.getDefault()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, null, options);
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
        }
        return null;
    }

}
```

Original_Constraint: ```
[checkStartActivityResult]: (<Parameter 0: int res> is ActivityManager.START_INTENT_NOT_RESOLVED OR <Parameter 0: int res> is ActivityManager.START_CLASS_NOT_FOUND) AND (NOT (<Parameter 1: Object intent> is an instance of Intent with a non-null component))
```
"""

INFERRER_ASSISTANT_EXAMPLE3 = """
Analysis: ```
The method `execStartActivity` in the `Instrumentation` class calls the method `checkStartActivityResult` with the parameters `result` and `intent`. 

The constraint on `checkStartActivityResult` states that an exception will be triggered if the first parameter (`res`) is either `ActivityManager.START_INTENT_NOT_RESOLVED` or `ActivityManager.START_CLASS_NOT_FOUND`, and the second parameter (`intent`) is not an instance of `Intent` or if it is an instance of `Intent`, its `getComponent()` method returns null.

In the context of `execStartActivity`, the `result` variable that is passed as the first parameter to `checkStartActivityResult` is the return value of the `startActivity` method called on `ActivityManagerNative.getDefault()`.

The `intent` variable passed as the second parameter to `checkStartActivityResult` is the same `intent` that is received by `execStartActivity`.

There are no transformations or conditions applied to `result` or `intent` before calling `checkStartActivityResult`, except for the calls to `intent.migrateExtraStreamToClipData()` and `intent.prepareToLeaveProcess()`, which do not affect the type or component of the `intent`.

Therefore, the constraint from `checkStartActivityResult` can be directly translated to the parameters of `execStartActivity` with respect to the `result` and `intent` variables.
```

Constraint: ```
[execStartActivity]: <Parameter 4: Intent intent> is not an instance of Intent or <Parameter 4: Intent intent> as Intent has a null getComponent() result, and the return value of ActivityManagerNative.getDefault().startActivity(...) is either <Field ActivityManager: int START_INTENT_NOT_RESOLVED> or <Field ActivityManager: int START_CLASS_NOT_FOUND>.
```
"""

INFERRER_INIT_PROMPT = []
INFERRER_INIT_PROMPT.append({"role": "system", "content": INFERRER_SYSTEM_PROMPT})
INFERRER_INIT_PROMPT.append({"role": "user", "content": INFERRER_USER_EXAMPLE1})
INFERRER_INIT_PROMPT.append(
    {"role": "assistant", "content": INFERRER_ASSISTANT_EXAMPLE1}
)

INFERRER_USER_PROMPT = (
    lambda code, constraint: f"""
Code: ```
{code}
```

Original_Constraint: ```
{constraint}
```
"""
)

EXPLAINER_SYSTEM_PROMPT = """
You are an Android expert that assist with explaining the crash of Android application.

For the crash, we will provide you with the following information:
1. The crash information which include crash message and stack trace
2. A constraint that applies to specific Android API, triggering this crash when this constraint is met.

Then, we will provide a group of suspicious method with following information:
1. The code snippet of a suspicious method which is detected by our static analysis tool and may cause the crash
2. The reason why the suspicious method is detected

Your task is to help the developers explain how these method caused the crash using information provided and your Android professional knowledge.

Please use the following format for the conversation.
Crash_Information: ```
Crash message and stack trace
```

Constraint: ```
The constraint that applies to specific Android API and trigger this crash
```

Suspicious_Method: ```
The code snippet of suspicious method
```

Reason: ```
The reason why the suspicious method is detected
```

Analysis: ```
Your detailed analysis process.

This part will be hidden from developers. Do not quote content from this part.
```

Android_Knowledge: ```
You need to assume that the developer has no relevant knowledge of Android frameworks. If your explanation requires any knowledge of Android frameworks, please briefly describe it in this section.

This part is optional.
```

Explanation: ```
A clear and concise explanation for developers in no more than 3 sentences.
```
"""

EXPLAINER_INIT_PROMPT = []
EXPLAINER_INIT_PROMPT.append({"role": "system", "content": EXPLAINER_SYSTEM_PROMPT})

EXPLAINER_CRASH_PROMPT = (
    lambda crash_info, constraint: f"""
Crash_Information: ```
{crash_info}
```

Constraint: ```
{constraint}
```
"""
)

EXPLAINER_USER_PROMPT = (
    lambda method_code, reason: f"""
Suspicious_Method: ```
{method_code}
```

Reason: ```
{reason}
```
"""
)

KEY_VAR_TERMINAL_PROMPT = lambda framework_entry_api, call_chain: f"""
Our static analysis tool detect that some buggy parameter value is passed to `{framework_entry_api}` by call chain {call_chain}.

The buggy parameter meet the crash constraint which was described in `Constraint` part
"""

KEY_VAR_NON_TERMINAL_AFTER_TERMINAL_PROMPT = lambda entry_api, terminal_api, call_chain: f"""
Our static analysis tool detect that the method invoke `{terminal_api}` by call chain {call_chain}

`{terminal_api}` method pass buggy parameter to `{entry_api}`

The buggy parameter meet the crash constraint which was described in `Constraint` part
"""

KEY_VAR_NON_TERMINAL_BEFORE_TERMINAL_PROMPT = lambda entry_api, call_chain: f"""
Our static analysis detect that some parameter meet the crash constraints described in the 'Constraint' section

this parameter is passed to `{entry_api}` through the call chain `{call_chain}`.
"""

KEY_VAR_MODIFIED_FIELD_PROMPT = lambda field, passed_method: f"""
Our static analysis detect that the method change the value of field `{field}`

The field was passed to the method `{passed_method}` and meet the crash constraint, resulting in the crash.
"""

KEY_API_INVOKED_PROMPT = lambda key_api, field, effect: f"""
We detect that the method `{key_api}` is invoked in the method.

The method can affect the `{field}` field in Android Framework so that cause constraint violation.

The method has following effect on the field:
{effect}
"""

KEY_API_EXECUTED_PROMPT = lambda :f"""
This method was detected because it was executed during the process of the application crashing.
"""

# Extract KeyAPI role

KEY_API_ROLE_SUMMARIZER_SYSTEM_PROMPT = """
You are an Android expert that assist with summarizing the effect of the method on specific fields in Android Framework.

You will be provided with the necessary information with following format:

Target Method: ```
The full signature of target method.
```

Target Field: ```
The full signature of target field.
```

Call Chain: ```
The call chain from target method to target field.
```

Code Snippet: ```
The code snippets of methods in call chain.
```

Your task is to summarize the effect of the method on target fields in Android Framework using following format:

Analysis: ```
Your detailed analysis process, should be step by step.

Only you can see this part. Do not quote content from this part.
```

Effect: ```
The effect of the method on target fields.
```
"""

KEY_API_ROLE_SUMMARIZER_USER_PROMPT = (
    lambda target_method, target_field, call_chain, code_snippet: f"""
Target Method: ```
{target_method}
```

Target Field: ```
{target_field}
```

Call Chain: ```
{call_chain}
```

Code Snippet: ```
{code_snippet}
```
"""
)

KEY_API_ROLE_SUMMARIZER_INIT_PROMPT = [
    {"role": "system", "content": KEY_API_ROLE_SUMMARIZER_SYSTEM_PROMPT}
]

EXTRACTOR_SYSTEM_PLAIN_PROMPT = """
Your task is to extract the precondition constraint of the target exception in Java methods and convert them into constraint related to method parameters or class field.

Please use the following format for the conversation:
Code: ```
Java method code
```

Exception: ```
Target exception type name and a possible exception message
```

Constraint: ```
Constraint related to method parameters. The method will throw **target exception** when this constraint is met.

// Note: Constraints should only be extracted using information from the current method snippet, without incorporating your Android knowledge. Constraints should be as formalized as possible.
```
"""
EXTRACTOR_PLAIN_INIT_PROMPT = []
EXTRACTOR_PLAIN_INIT_PROMPT.append({"role": "system", "content": EXTRACTOR_SYSTEM_PLAIN_PROMPT})

plain_explanation_prompt = """
You are an Computer expert that assist with explaining the crash of Android application.

For the crash, we will provide you with the following information:
1. The crash information which include crash message and stack trace

Then, we will provide a group of suspicious method with following information:
1. The code snippet of a suspicious method

Your task is to help the developers explain how these method caused the crash using information provided.

Please use the following format for the conversation.
Crash_Information: ```
Crash message and stack trace
```

Suspicious_Method: ```
The code snippet of suspicious method
```

Explanation: ```
A clear and concise explanation for developers in no more than 3 sentences.
```
"""

GLOBAL_SUMMARY_SYSTEM_PROMPT = """
You are an Computer expert that assist with explaining the crash of Android application.

For the crash, we will provide you with the following information:
1. The crash information which include crash message and stack trace
2. A group of candidate method which may cause the crash, the reason why they are detected and the explanation of the candidate if exists.

Your task is to help the developers summary these all candidate method and explain how these method caused the crash using information provided.

Please use the following format for the conversation.

Summary: ```
Summary of how all these candidates caused the crash.
```
"""

GLOBAL_SUMMARY_USER_CRASH_INFORMATION_PROMPT = (
    lambda exception_name, crash_message, stack_trace: f"""
Crash Information: ```
Exception Name: {exception_name}
Exception Message: {crash_message}
Stack Trace: {stack_trace}
```
"""
)

GLOBAL_SUMMARY_USER_CANDIDATE_PROMPT = (
    lambda num, name, reason, explanation: f"""
Candidate {num}: ```
Method Name: {name}
Reason: {reason}
{f"Explanation: {explanation}" if explanation else ""}
```
"""
)