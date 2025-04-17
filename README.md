# PensumAI - Competence Goal Matcher

A Java application that matches developer skills with competence goals using Large Language Models (LLMs). This tool helps developers identify which competence goals they meet based on their work descriptions.PS: The application works best when it is run with GitHub Models.

## Overview

The Competence Goal Matcher:
1. Loads competence goals and subgoals from JSON files
2. Prompts developers to describe their recent tasks and skills
3. Uses LLM technology to analyze and match the developer's skills with relevant competence goals
4. Displays matching competence goals and specific subgoals that align with the developer's experience

The application is configurable and supports:
- Multiple languages (English and Norwegian)
- Different LLM providers (Ollama and GitHub Models)
- Various LLM models for different quality/performance needs

## Prerequisites

- Java 23 or higher
- Maven for building
- One of the following LLM providers:
   - **Ollama** (default): For local, private model hosting
   - **GitHub Models API**: For cloud-based model access

### Setting up Ollama (Default Provider)

1. Install Ollama from [https://ollama.ai/](https://ollama.ai/)
2. Pull a supported model (default is qwen2.5:32b):
```shell script
ollama pull qwen2.5:32b
```
3. Ensure Ollama is running:
```shell script
ollama serve
```

### Setting up GitHub Models API (Alternative)

1. Create a GitHub Personal Access Token with appropriate permissions
2. Export the token as an environment variable:
```shell script
export GH_TOKEN=your_github_token_here
```

## Building the Application

```shell script
mvn clean package
```

## Running the Application

### Basic Usage

```shell script
# Run with default settings (English, Ollama provider, qwen2.5:32b:32b model)
java -jar target/pensumai.jar
```

### Advanced Options

```shell script
# Run with Norwegian competence goals
java -jar target/pensumai.jar --language no

# Use GitHub Models as provider
java -jar target/pensumai.jar --provider GITHUB_MODELS

# Use a specific Ollama model
java -jar target/pensumai.jar --ollama-model llama3

# Use a specific GitHub model
java -jar target/pensumai.jar --provider GITHUB_MODELS --github-model GPT_4_O_MINI

# Display help
java -jar target/pensumai.jar --help
```

### Command Line Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--language <code>` | `-l` | Language code for competence goals | `en` |
| `--provider <provider>` | `-p` | LLM provider (OLLAMA or GITHUB_MODELS) | `OLLAMA` |
| `--ollama-model <model>` | `-om` | Ollama model to use | `qwen2.5:32b:32b` |
| `--github-model <model>` | `-gm` | GitHub model to use | `GPT_4_O_MINI` |
| `--help` | `-h` | Show help message | |

## Interactive Usage

1. Launch the application with your preferred options
2. The application will load competence goals from the appropriate JSON file
3. You'll be prompted to describe your recent tasks and skills
4. Type your response and enter `done` on a new line when finished
5. The application will analyze your input and match it with competence goals
6. Matching goals and specific subgoals will be displayed

## Example Session

```
Loaded 21 competence goals.

Please describe the tasks you performed in your recent work:
(Type your response and press Enter, then type 'done' on a new line to finish)
I've been developing a Java Spring Boot application with RESTful APIs. 
I implemented secure authentication using JWT and OAuth2.
I've written unit tests with JUnit and performed code reviews.
We use Git for version control and GitHub Actions for CI/CD.
done

Matching your response to competence goals...

Matching Competence Goals:
==========================

Goal 1: Plan, develop and document solutions with built-in privacy and security
Matching subgoals:
  • Implement security measures in application code
  • Use version control systems effectively
  • Document development processes and security considerations

Goal 7: Write maintainable and testable code
Matching subgoals:
  • Apply test-driven development principles
  • Conduct code reviews to ensure quality
  • Create automated tests for application components
```

## Project Structure

- `src/main/java/no/dervis/`
   - `model/`: Data models for competence goals
   - `service/`:
      - `CompetenceGoalService.java`: Loads and manages competence goals
      - `LlmService.java`: Interfaces with LLM providers for matching
   - `App.java`: Main application class with UI logic
- `src/main/resources/`:
   - JSON files containing competence goals in different languages
   - LLM prompt templates

## Supported LLM Providers

### Ollama
- Free, locally-hosted models for privacy and no API costs
- Supports various models like llama3, qwen2.5:32b, mistral, etc.
- Requires local setup and model downloads

### GitHub Models API
- Cloud-based, high-quality models
- Requires GitHub authentication and API token
- Supports models like GPT_4_O_MINI, CLAUDE_3_5_SONNET, and others

## License
This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing
Contributions are welcome! Please feel free to submit a Pull Request.
