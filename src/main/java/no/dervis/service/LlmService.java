package no.dervis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.github.GitHubModelsChatModel;
import dev.langchain4j.model.github.GitHubModelsChatModelName;
import dev.langchain4j.model.ollama.OllamaChatModel;
import no.dervis.model.CompetenceGoal;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;

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
        GITHUB_MODELS
    }

    // Record for JSON deserialization
    private record MatchResult(int competenceGoalId, List<String> matchingSubGoals) {}

    // Service dependencies
    private final ObjectMapper objectMapper;
    private final String ollamaEndpoint;
    private final String defaultOllamaModel;
    private final GitHubModelsChatModelName defaultGithubModel;
    private final LlmProvider defaultProvider;

    /**
     * Creates a new LlmService with Ollama as the default provider.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param ollamaEndpoint URL endpoint for Ollama API
     * @param defaultOllamaModel Default model name to use with Ollama
     */
    public LlmService(ObjectMapper objectMapper, String ollamaEndpoint, String defaultOllamaModel) {
        this(objectMapper, ollamaEndpoint, defaultOllamaModel, GitHubModelsChatModelName.GPT_4_O_MINI, LlmProvider.OLLAMA);
    }

    /**
     * Creates a new LlmService with GitHub Models as the default provider.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param defaultGithubModel Default GitHub model to use
     */
    public LlmService(ObjectMapper objectMapper, GitHubModelsChatModelName defaultGithubModel) {
        this(objectMapper, null, null, defaultGithubModel, LlmProvider.GITHUB_MODELS);
    }

    /**
     * Creates a new LlmService with complete configuration.
     *
     * @param objectMapper Jackson object mapper for JSON processing
     * @param ollamaEndpoint URL endpoint for Ollama API (can be null if not using Ollama)
     * @param defaultOllamaModel Default model name for Ollama (can be null if not using Ollama)
     * @param defaultGithubModel Default GitHub model (can be null if not using GitHub Models)
     * @param defaultProvider The default LLM provider to use
     */
    public LlmService(
            ObjectMapper objectMapper,
            String ollamaEndpoint,
            String defaultOllamaModel,
            GitHubModelsChatModelName defaultGithubModel,
            LlmProvider defaultProvider) {

        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.defaultProvider = Objects.requireNonNull(defaultProvider, "Default provider must not be null");

        // Validate provider-specific parameters
        if (defaultProvider == LlmProvider.OLLAMA) {
            this.ollamaEndpoint = Objects.requireNonNull(ollamaEndpoint, "Ollama endpoint must not be null when using Ollama");
            this.defaultOllamaModel = Objects.requireNonNull(defaultOllamaModel, "Default Ollama model must not be null when using Ollama");
        } else {
            this.ollamaEndpoint = ollamaEndpoint; // Can be null
            this.defaultOllamaModel = defaultOllamaModel; // Can be null
        }

        if (defaultProvider == LlmProvider.GITHUB_MODELS) {
            this.defaultGithubModel = Objects.requireNonNull(defaultGithubModel, "Default GitHub model must not be null when using GitHub Models");
        } else {
            this.defaultGithubModel = defaultGithubModel; // Can be null
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

        if (defaultProvider == LlmProvider.OLLAMA) {
            return matchCompetenceGoalsWithOllama(developerResponse, competenceGoals, defaultOllamaModel);
        } else {
            return matchCompetenceGoalsWithGitHubModel(developerResponse, competenceGoals, defaultGithubModel);
        }
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
     *
     * @param developerResponse The developer's description of their tasks
     * @param competenceGoals The list of competence goals to match against
     * @param githubModel The GitHub model to use
     * @return A list of matching competence goals with their matching subgoals
     * @throws IOException If an I/O error occurs during LLM communication
     * @throws InterruptedException If the operation is interrupted
     */
    public List<CompetenceGoal> matchCompetenceGoalsWithGitHubModel(
            String developerResponse,
            List<CompetenceGoal> competenceGoals,
            GitHubModelsChatModelName githubModel) throws IOException, InterruptedException {

        String prompt = createMatchingPrompt(developerResponse, competenceGoals);
        String llmResponse = generateGitHubModelResponse(prompt, githubModel);
        return parseMatchingResponse(llmResponse, competenceGoals);
    }

    /**
     * Generates a response using Ollama model.
     */
    private String generateOllamaResponse(String prompt, String modelName) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaEndpoint)
                .modelName(modelName)
                .timeout(DEFAULT_TIMEOUT)
                .build();

        return model.chat(prompt);
    }

    /**
     * Generates a response using GitHub Models.
     */
    private String generateGitHubModelResponse(String prompt, GitHubModelsChatModelName githubModel) {
        GitHubModelsChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(GH_TOKEN)
                .modelName(githubModel)
                .logRequestsAndResponses(false)
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