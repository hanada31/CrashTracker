import openai
from openai import OpenAI
from openai._types import NOT_GIVEN
from ExplanationGenerator.config import logger, GPT_MODEL, TEMPERATURE, SEED
from termcolor import colored
import os
import json

from ExplanationGenerator.config import OPENAI_API_KEY, OPENAI_BASH_URL
client = OpenAI(api_key=OPENAI_API_KEY, base_url=OPENAI_BASH_URL)


def send_message(messages, tools=None, tool_choice=None, json_mode=False):
    try:
        conversation = messages.copy()
        logger.info(f"Sending message, waiting for response...")
        response = client.chat.completions.create(
            model=GPT_MODEL,
            messages=messages,
            temperature=TEMPERATURE,
            seed=SEED,
            tools=tools if tools is not None else NOT_GIVEN,
            tool_choice=tool_choice if tool_choice is not None else NOT_GIVEN,
            response_format={"type": "json_object"} if json_mode else NOT_GIVEN,
            timeout=240,
        )
        logger.info(f"Received response!")
        response_message = json.loads(response.model_dump_json())["choices"][0][
            "message"
        ]
        # delete all attribute but 'role' and 'content'
        for key in list(response_message.keys()):
            if key not in ["role", "content"]:
                del response_message[key]
        conversation.append(response_message)
        return conversation, response.system_fingerprint
    except openai.APIConnectionError as e:
        logger.error(f"Failed to connect to OpenAI API: {e}")
        exit()
    except openai.APIError as e:
        logger.error(f"OpenAI API returned an API Error: {e}")
        exit()


def message_writer(message, colorful: bool, write_function):
    role_to_color = {
        "system": "red",
        "user": "green",
        "assistant": "blue",
        "function": "magenta",
    }
    color = role_to_color[message["role"]] if colorful else None

    def colorize(text, color):
        return colored(text, color) if color else text

    if message["role"] == "system":
        write_function(colorize(f"system:\n{message['content']}\n", color))
    elif message["role"] == "user":
        write_function(colorize(f"user:\n{message['content']}\n", color))
    elif message["role"] == "assistant" and message.get("function_call"):
        write_function(
            colorize(
                f"assistant function_call:\n{message['function_call']['name']}\n{message['function_call']['arguments']}\n",
                color,
            )
        )
    elif message["role"] == "assistant" and not message.get("function_call"):
        write_function(colorize(f"assistant:\n{message['content']}\n", color))
    elif message["role"] == "function":
        write_function(
            colorize(f"function ({message['name']}): {message['content']}\n", color)
        )


def conversation_writer(messages, colorful: bool, write_function):
    for message in messages:
        message_writer(message, colorful=colorful, write_function=write_function)
        write_function("-----------------------------------")


def pretty_print_conversation(messages):
    conversation_writer(messages, colorful=True, write_function=print)


def write_conversation(messages, file_name):
    with open(file_name, "w") as f:
        conversation_writer(
            messages, colorful=False, write_function=lambda x: f.write(x + "\n")
        )


def pretty_log_conversation(messages):
    conversation_writer(messages, colorful=False, write_function=logger.debug)


def save_messages(message, directory, file_name):
    with open(f"{directory}/{file_name}.json", "w") as f:
        json.dump(message, f, indent=4)
    write_conversation(message, f"{directory}/{file_name}.txt")
