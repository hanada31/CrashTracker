from enum import Enum

class ReasonType(Enum):
    KEY_VAR_TERMINAL = "Key Variable Related 1"
    KEY_VAR_NON_TERMINAL = "Key Variable Related 2"
    KEY_API_INVOKED = "Key API Related 1"
    KEY_API_EXECUTED = "Key API Related 2 (Executed)"
    KEY_VAR_MODIFIED_FIELD = "Key Variable Related 4" # The method change some field value which is passed into crash API
    