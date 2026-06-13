package no.dervis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import no.dervis.copilot.CopilotTokenService;
import no.dervis.model.CompetenceGoal;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for matching developer responses to competence goals using LLM.
 * Supports both Ollama and GitHub Models as LLM providers.
 */
public class LlmService {
    // Constants
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    public static final String GH_TOKEN = System.getenv("GH_TOKEN");

    /**
     * Enum defining available LLM provider types
     */
    public enum LlmProvider {
        OLLAMA,
        GITHUB_MODELS,
        GITHUB_COPILOT
    }

    // Record for JSON deserialization
    private record MatchResult(int competenceGoalId, List<String> matchingSubGoals) {}

    // Service dependencies
    private final ObjectMapper objectMapper;
    private final String ollamaEndpoint;
    private final String defaultOllamaModel;
    private final String defaultGithubModel;
    private final LlmProvider defaultProvider;
    private final CopilotTokenService copilotTokenService;
    private final String defaultCopilotModel;

    /**
     * Creates a new LlmService with Ollama as the default provider.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param ollamaEndpoint URL endpoint for Ollama API
     * @param defaultOllamaModel Default model name to use with Ollama
     */
    public LlmService(ObjectMapper objectMapper, String ollamaEndpoint, String defaultOllamaModel) {
        this(objectMapper, ollamaEndpoint, defaultOllamaModel, "gpt-4o-mini",
                null, null, LlmProvider.OLLAMA);
    }

    /**
     * Creates a new LlmService with GitHub Models as the default provider.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param defaultGithubModel Default GitHub model to use
     */
    public LlmService(ObjectMapper objectMapper, String defaultGithubModel) {
        this(objectMapper, null, null, defaultGithubModel, null, null, LlmProvider.GITHUB_MODELS);
    }

    /**
     * Creates a new LlmService with GitHub Copilot as the default provider.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param copilotTokenService Service that provides Copilot bearer tokens
     * @param defaultCopilotModel Default Copilot model id (e.g. "claude-opus-4.7")
     */
    public LlmService(ObjectMapper objectMapper,
                      CopilotTokenService copilotTokenService,
                      String defaultCopilotModel) {
        this(objectMapper, null, null, null,
                Objects.requireNonNull(copilotTokenService, "CopilotTokenService must not be null"),
                Objects.requireNonNull(defaultCopilotModel, "Default Copilot model must not be null"),
                LlmProvider.GITHUB_COPILOT);
    }

    /**
     * Creates a new LlmService with complete configuration.
     */
    public LlmService(
            ObjectMapper objectMapper,
            String ollamaEndpoint,
            String defaultOllamaModel,
            String defaultGithubModel,
            CopilotTokenService copilotTokenService,
            String defaultCopilotModel,
            LlmProvider defaultProvider) {

        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.defaultProvider = Objects.requireNonNull(defaultProvider, "Default provider must not be null");

        // Validate provider-specific parameters
        if (defaultProvider == LlmProvider.OLLAMA) {
            this.ollamaEndpoint = Objects.requireNonNull(ollamaEndpoint, "Ollama endpoint must not be null when using Ollama");
            this.defaultOllamaModel = Objects.requireNonNull(defaultOllamaModel, "Default Ollama model must not be null when using Ollama");
        } else {
            this.ollamaEndpoint = ollamaEndpoint;
            this.defaultOllamaModel = defaultOllamaModel;
        }

        if (defaultProvider == LlmProvider.GITHUB_MODELS) {
            this.defaultGithubModel = Objects.requireNonNull(defaultGithubModel, "Default GitHub model must not be null when using GitHub Models");
        } else {
            this.defaultGithubModel = defaultGithubModel;
        }

        if (defaultProvider == LlmProvider.GITHUB_COPILOT) {
            this.copilotTokenService = Objects.requireNonNull(copilotTokenService, "CopilotTokenService must not be null when using GitHub Copilot");
            this.defaultCopilotModel = Objects.requireNonNull(defaultCopilotModel, "Default Copilot model must not be null when using GitHub Copilot");
        } else {
            this.copilotTokenService = copilotTokenService;
            this.defaultCopilotModel = defaultCopilotModel;
        }
    }

    /**
     * Matches developer's response to competence goals using the default LLM provider and model.
     *
     * @param developerResponse The developer's description of their tasks
     * @param competenceGoals The list of competence goals to match against
     * @return A list of matching competence goals with their matching subgoals
     * @throws IOException If an I/O error occurs during LLM communication
     * @throws InterruptedException If the operation is interrupted
     */
    public List<CompetenceGoal> matchCompetenceGoals(String developerResponse, List<CompetenceGoal> competenceGoals)
            throws IOException, InterruptedException {

        return switch (defaultProvider) {
            case OLLAMA -> matchCompetenceGoalsWithOllama(developerResponse, competenceGoals, defaultOllamaModel);
            case GITHUB_MODELS -> matchCompetenceGoalsWithGitHubModel(developerResponse, competenceGoals, defaultGithubModel);
            case GITHUB_COPILOT -> matchCompetenceGoalsWithCopilot(developerResponse, competenceGoals, defaultCopilotModel);
        };
    }


    /**
     * Matches developer's response to competence goals using Ollama with the specified model.
     *
     * @param developerResponse The developer's description of their tasks
     * @param competenceGoals The list of competence goals to match against
     * @param modelName The Ollama model name to use
     * @return A list of matching competence goals with their matching subgoals
     * @throws IOException If an I/O error occurs during LLM communication
     * @throws InterruptedException If the operation is interrupted
     * @throws IllegalStateException If Ollama is not properly configured
     */
    public List<CompetenceGoal> matchCompetenceGoalsWithOllama(
            String developerResponse,
            List<CompetenceGoal> competenceGoals,
            String modelName) throws IOException, InterruptedException {

        if (ollamaEndpoint == null) {
            throw new IllegalStateException("Ollama endpoint is not configured");
        }

        String prompt = createMatchingPrompt(developerResponse, competenceGoals);
        String llmResponse = generateOllamaResponse(prompt, modelName);
        return parseMatchingResponse(llmResponse, competenceGoals);
    }

    /**
     * Matches developer's response to competence goals using a GitHub model.
     */
    public List<CompetenceGoal> matchCompetenceGoalsWithGitHubModel(
            String developerResponse,
            List<CompetenceGoal> competenceGoals,
            String githubModel) throws IOException, InterruptedException {

        String prompt = createMatchingPrompt(developerResponse, competenceGoals);
        String llmResponse = generateGitHubModelResponse(prompt, githubModel);
        return parseMatchingResponse(llmResponse, competenceGoals);
    }

    /**
     * Matches developer's response to competence goals using a GitHub Copilot model.
     *
     * @param developerResponse The developer's description of their tasks
     * @param competenceGoals The list of competence goals to match against
     * @param copilotModel The Copilot model id (e.g. "claude-opus-4.7")
     */
    public List<CompetenceGoal> matchCompetenceGoalsWithCopilot(
            String developerResponse,
            List<CompetenceGoal> competenceGoals,
            String copilotModel) throws IOException, InterruptedException {

        if (copilotTokenService == null) {
            throw new IllegalStateException("Copilot token service is not configured");
        }
        String prompt = createMatchingPrompt(developerResponse, competenceGoals);
        String llmResponse = generateCopilotResponse(prompt, copilotModel);
        return parseMatchingResponse(llmResponse, competenceGoals);
    }

    /**
     * Generates a response using Ollama model.
     */
    private String generateOllamaResponse(String prompt, String modelName) {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaEndpoint)
                .modelName(modelName)
                .timeout(DEFAULT_TIMEOUT)
                .build();

        return model.chat(prompt);
    }

    /**
     * Generates a response using GitHub Models via their OpenAI-compatible inference endpoint.
     */
    private String generateGitHubModelResponse(String prompt, String githubModel) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .apiKey(GH_TOKEN)
                .modelName(githubModel)
                .timeout(DEFAULT_TIMEOUT)
                .logRequests(false)
                .logResponses(false)
                .build();

        return model.chat(prompt);
    }

    /**
     * Generates a response using GitHub Copilot via its OpenAI-compatible chat-completions endpoint.
     */
    private String generateCopilotResponse(String prompt, String copilotModel) throws IOException, InterruptedException {
        String copilotToken = copilotTokenService.getToken();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.githubcopilot.com")
                .apiKey(copilotToken)
                .modelName(copilotModel)
                .timeout(DEFAULT_TIMEOUT)
                .customHeaders(Map.of(
                        "Copilot-Integration-Id", "vscode-chat",
                        "Editor-Version", "vscode/1.95.0",
                        "Editor-Plugin-Version", "copilot-chat/0.23.0",
                        "User-Agent", "GitHubCopilotChat/0.23.0",
                        "OpenAI-Intent", "conversation-panel"
                ))
                .logRequests(false)
                .logResponses(false)
                .build();

        return model.chat(prompt);
    }

    /**
     * Creates a prompt for the LLM to match developer response to competence goals.
     */
    private String createMatchingPrompt(String developerResponse, List<CompetenceGoal> competenceGoals) {
        try {
            String goalsJson = objectMapper.writeValueAsString(competenceGoals);

            return String.format("""
                You are an AI assistant that helps match developer responses to competence goals.
                
                COMPETENCE GOALS:
                %s
                
                DEVELOPER RESPONSE:
                %s
                
                <think>
                Analyze the developer's response and identify which competence goals it matches.
                For each matching goal, identify which specific subgoals are matched.
                </think>
                
                Return a JSON array of matching competence goals in this format:
                [
                  {
                    "competenceGoalId": 123,
                    "matchingSubGoals": ["subgoal1", "subgoal2"]
                  }
                ]
                Only include goals where there is a clear match to the developer's response.
                """, goalsJson, developerResponse);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize competence goals", e);
        }
    }

    /**
     * Parses the LLM response to extract matching competence goals.
     */
    private List<CompetenceGoal> parseMatchingResponse(String llmResponse, List<CompetenceGoal> allGoals) {
        try {
            // Remove thinking sections and extract JSON array
            String cleanedResponse = THINK_TAG_PATTERN.matcher(llmResponse).replaceAll("");
            Matcher jsonMatcher = JSON_ARRAY_PATTERN.matcher(cleanedResponse);

            if (!jsonMatcher.find()) {
                return List.of();
            }

            // Create lookup map for competence goals
            Map<Integer, CompetenceGoal> goalMap = allGoals.stream()
                    .collect(Collectors.toMap(CompetenceGoal::getId, goal -> goal));

            // Parse and transform results
            return objectMapper.readValue(jsonMatcher.group(),
                            new TypeReference<List<MatchResult>>() {})
                    .stream()
                    .filter(match -> goalMap.containsKey(match.competenceGoalId))
                    .map(match -> {
                        CompetenceGoal original = goalMap.get(match.competenceGoalId);
                        return new CompetenceGoal(
                                original.getId(),
                                original.getTitle(),
                                match.matchingSubGoals != null ? match.matchingSubGoals : List.of()
                        );
                    })
                    .toList();

        } catch (Exception e) {
            System.err.println("Error parsing LLM response: " + e.getMessage());
            return List.of();
        }
    }
}