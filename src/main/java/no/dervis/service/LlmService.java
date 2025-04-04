package no.dervis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.dervis.model.CompetenceGoal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the LLM using direct HTTP requests to Ollama.
 */
public class LlmService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String ollamaEndpoint;
    private final String modelName;

    /**
     * Creates a new LlmService that connects to Ollama with the specified model.
     */
    public LlmService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.ollamaEndpoint = "http://localhost:11434/api/generate";
        this.modelName = "qwen2.5-coder:32b";
    }

    /**
     * Matches the developer's response to competence goals.
     * 
     * @param developerResponse The developer's description of their tasks
     * @param competenceGoals The list of competence goals to match against
     * @return A list of matching competence goals with their matching subgoals
     */
    public List<CompetenceGoal> matchCompetenceGoals(String developerResponse, List<CompetenceGoal> competenceGoals) {
        try {
            // Create a prompt for the LLM
            String prompt = createMatchingPrompt(developerResponse, competenceGoals);

            // Get the response from the LLM
            String response = generateResponse(prompt);

            // Parse the response to extract matching competence goals
            return parseMatchingResponse(response, competenceGoals);
        } catch (Exception e) {
            System.err.println("Error matching competence goals: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Sends a request to Ollama and gets the response.
     */
    private String generateResponse(String prompt) throws IOException, InterruptedException {
        // Create the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("prompt", prompt);
        requestBody.put("temperature", 0.1);

        // Convert the request body to JSON
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        // Send the request and get the response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse the response to extract the generated text
        Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
        return (String) responseMap.get("response");
    }

    /**
     * Creates a prompt for the LLM to match developer response to competence goals.
     */
    private String createMatchingPrompt(String developerResponse, List<CompetenceGoal> competenceGoals) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("I'm going to provide you with a developer's description of their tasks and a list of competence goals with subgoals. ");
        prompt.append("Your task is to identify which competence goals and specific subgoals match the skills demonstrated in the developer's description. ");
        prompt.append("Please respond with a JSON array of objects, where each object contains 'competenceGoalId', 'competenceGoalTitle', and 'matchingSubGoals' (an array of matching subgoal strings). ");
        prompt.append("Only include competence goals that have at least one matching subgoal.\n\n");

        prompt.append("Developer's description:\n").append(developerResponse).append("\n\n");

        prompt.append("Competence Goals:\n");
        for (CompetenceGoal goal : competenceGoals) {
            prompt.append("ID: ").append(goal.getId()).append("\n");
            prompt.append("Title: ").append(goal.getTitle()).append("\n");
            prompt.append("Subgoals:\n");
            for (String subgoal : goal.getSubGoals()) {
                prompt.append("- ").append(subgoal).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Please analyze the developer's description and identify matching competence goals and subgoals. ");
        prompt.append("Return your response as a JSON array in the format described above.");

        return prompt.toString();
    }

    /**
     * Parses the LLM response to extract matching competence goals.
     */
    private List<CompetenceGoal> parseMatchingResponse(String llmResponse, List<CompetenceGoal> allGoals) {
        List<CompetenceGoal> matchingGoals = new ArrayList<>();

        try {
            // Try to parse the response as JSON
            if (llmResponse.contains("[") && llmResponse.contains("]")) {
                // Extract the JSON array from the response
                String jsonStr = llmResponse.substring(
                        llmResponse.indexOf("["),
                        llmResponse.lastIndexOf("]") + 1
                );

                // Parse the JSON array
                List<Map<String, Object>> matches = objectMapper.readValue(
                        jsonStr,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                // Process each matching goal
                for (Map<String, Object> match : matches) {
                    // Extract the competence goal ID
                    int goalId;
                    if (match.containsKey("competenceGoalId")) {
                        goalId = ((Number) match.get("competenceGoalId")).intValue();
                    } else {
                        continue; // Skip if no ID is provided
                    }

                    // Find the corresponding goal in allGoals
                    CompetenceGoal originalGoal = allGoals.stream()
                            .filter(g -> g.getId() == goalId)
                            .findFirst()
                            .orElse(null);

                    if (originalGoal == null) {
                        continue; // Skip if the goal is not found
                    }

                    // Extract the matching subgoals
                    List<String> matchingSubGoals;
                    if (match.containsKey("matchingSubGoals")) {
                        matchingSubGoals = (List<String>) match.get("matchingSubGoals");
                    } else {
                        matchingSubGoals = new ArrayList<>();
                    }

                    // Create a new CompetenceGoal with only the matching subgoals
                    CompetenceGoal matchingGoal = new CompetenceGoal(
                            originalGoal.getId(),
                            originalGoal.getTitle(),
                            matchingSubGoals
                    );

                    matchingGoals.add(matchingGoal);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing LLM response: " + e.getMessage());
            e.printStackTrace();

            // Fallback: Return all goals if parsing fails
            return new ArrayList<>(allGoals);
        }

        return matchingGoals;
    }
}
