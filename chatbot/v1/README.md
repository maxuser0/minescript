## `chatbot v1`

Get responses from an AI chatbot that's aware of your Minecraft surroundings.

&nbsp;

**Requirements**

  Minescript v3.1 or higher
  [lib_nbt](https://minescript.net/sdm_downloads/lib_nbt) v1 or higher
  [OpenAI API key](https://beta.openai.com/account/api-keys) (run `chatbot` and follow instructions for setting up your key)

&nbsp;

**Usage**

Prompt chatbot to get a single response and exit:

```
\chatbot PROMPT
```

&nbsp;

Run chatbot in "interactive mode" in the background and have it respond to messages that match the regular expression PATTERN, with options to ignore upper/lower case and give the chatbot a name:

```
\chatbot -i PATTERN [ignorecase] [name=NAME]
```

&nbsp;

In interactive mode, chatbot output is prefixed with `>>>` and the bot can be stopped by entering `quitbot` into the chat.

&nbsp;

**Examples**

Ask chatbot a question and get a single response:

```
\chatbot "What am I looking at? And how long until sunrise?"
```

&nbsp;

Run chatbot interactively, responding to chat messages that include the phrase "bot," with any combination of upper/lower case:

```
\chatbot -i ".*\bbot,\s" ignorecase
```
