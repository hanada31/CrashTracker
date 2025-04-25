class LLMOutputFormatError(Exception):
    """
    Raised when the output of LLM is not in the expected format.
    """

    pass


class ConstraintBasicCheckError(Exception):
    """
    Raised when the basic constraint check fails.
    """

    pass


class ConstraintStaticAnalysisCheckError(Exception):
    """
    Raised when the pass chain constraint check fails.
    """

    pass


class ConstraintCheckError(Exception):
    """
    Raised when the constraint check fails.
    """

    pass


class TooMuchCandidateError(Exception):
    """
    Raised when the number of candidates is too large.
    """
    pass


class TooManyAttemptsError(Exception):
    """
    Raised when the number of attempts exceeds the limit.
    """
    pass


class CanNotExtractError(Exception):
    """
    Raised when the extraction fails.
    """
    pass