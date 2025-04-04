# Competence Goal Matcher

A Java application that uses LangChain4j and Ollama with a local LLM model (qwen2.5-coder:32b) to match developer skills with competence goals.

## Overview

This application:
1. Reads competence goals and subgoals from a JSON file
2. Asks a developer to describe their recent tasks and skills
3. Uses a local LLM (qwen2.5-coder:32b) to match the developer's skills with competence goals
4. Displays the matching competence goals and subgoals

The application supports both English and Norwegian competence goals, using the files `curriculum.json` and `pensum.json` respectively.

## Prerequisites

- Java 23 or later
- Maven
- [Ollama](https://ollama.ai/) installed and running locally
- The qwen2.5-coder:32b model pulled in Ollama

### Setting up Ollama

1. Install Ollama from [https://ollama.ai/](https://ollama.ai/)
2. Pull the qwen2.5-coder:32b model:
   ```
   ollama pull qwen2.5-coder:32b
   ```
3. Ensure Ollama is running:
   ```
   ollama serve
   ```

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
# Run with English competence goals (default)
java -jar target/pensumai.jar

# Run with Norwegian competence goals
java -jar target/pensumai.jar no
```

## Usage

1. Run the application
2. The application will load the competence goals from the JSON file
3. You will be prompted to describe your recent tasks and skills
4. Type your response and press Enter twice when you're done
5. The application will use the LLM to match your skills with competence goals
6. The matching competence goals and subgoals will be displayed

## Example

```
Loading competence goals...
Loaded 21 competence goals.

Please describe the tasks you have been working on recently.
Be specific about the technologies, methodologies, and skills you've used.
Type your response and press Enter twice when you're done:
I've been working on a Java application that uses Spring Boot and MongoDB. I've implemented RESTful APIs, written unit tests with JUnit, and used Git for version control. I've also been working on improving the application's security by implementing authentication and authorization with Spring Security.

Matching Competence Goals:
==========================

Competence Goal 1: Plan, develop and document solutions with built-in privacy and security
Matching Subgoals:
  - Development: Write code
  - Development: Use the company's version control
  - Testing: Bug fixing and code review according to the company's procedures

Competence Goal 8: Handle login information in a secure and responsible manner
Matching Subgoals:
  - Create and use consent forms in their work where necessary
  - Use the company's procedures and relevant legislation for collecting and storing personal information.
```

## Project Structure

- `src/main/java/no/dervis/model/`: Contains the model classes for competence goals
- `src/main/java/no/dervis/service/`: Contains the service classes for loading competence goals and interacting with the LLM
- `src/main/java/no/dervis/App.java`: The main application class
- `src/main/resources/`: Contains the JSON files with competence goals

## License

This project is licensed under the MIT License - see the LICENSE file for details.
