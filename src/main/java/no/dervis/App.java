package no.dervis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.github.GitHubModelsChatModelName;
import no.dervis.model.CompetenceGoal;
import no.dervis.service.CompetenceGoalService;
import no.dervis.service.LlmService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Main application class for the Competence Goal Matcher.
 * This application reads competence goals from a JSON file, asks the developer about their tasks,
 * and matches the developer's response to competence goals using an LLM.
 */
public class App {

    private static final String DEFAULT_LANGUAGE = "en"; // Default to English

    public static void main(String[] args) {
        try {
            // Create services
            LlmService ollamaLlm = new LlmService(
                    new ObjectMapper(),
                    "http://localhost:11434/api/generate",
                    "qwen2.5-coder:32b");

            LlmService ghLlm = new LlmService(
                    new ObjectMapper(),
                    GitHubModelsChatModelName.GPT_4_O_MINI);

            LlmService llmService = ghLlm;

            // Load competence goals
            String language = args.length > 0 ? args[0] : DEFAULT_LANGUAGE;
            List<CompetenceGoal> competenceGoals = loadCompetenceGoals(new CompetenceGoalService(), language);

            // Ask the developer about their tasks
            String developerResponse =  "I implemented a Java application and deployed it to Google Cloud"; //askDeveloper();

            System.out.println("\nAsking the LLM:\n");
            System.out.println(developerResponse);

            // Match the developer's response to competence goals
            List<CompetenceGoal> matchingGoals = llmService.matchCompetenceGoals(developerResponse, competenceGoals);

            // Display the matching competence goals
            displayMatchingGoals(matchingGoals);

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads competence goals from the JSON file based on the specified language.
     */
    private static List<CompetenceGoal> loadCompetenceGoals(CompetenceGoalService service, String language) throws IOException {
        System.out.println("Loading competence goals...");
        List<CompetenceGoal> goals = service.loadCompetenceGoals(language);
        System.out.println("Loaded " + goals.size() + " competence goals.");
        return goals;
    }

    /**
     * Asks the developer about their tasks and returns their response.
     */
    private static String askDeveloper() throws IOException {
        System.out.println("\nPlease describe the tasks you have been working on recently.");
        System.out.println("Be specific about the technologies, methodologies, and skills you've used.");
        System.out.println("Type your response and press Enter twice when you're done:");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder response = new StringBuilder();
        String line;

        // Read until an empty line is entered
        while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
            response.append(line).append("\n");
        }

        return response.toString().trim();
    }

    /**
     * Displays the matching competence goals and their subgoals.
     */
    private static void displayMatchingGoals(List<CompetenceGoal> matchingGoals) {
        if (matchingGoals.isEmpty()) {
            System.out.println("\nNo matching competence goals found.");
            return;
        }

        System.out.println("\nMatching Competence Goals:");
        System.out.println("==========================");

        for (CompetenceGoal goal : matchingGoals) {
            System.out.println("\nCompetence Goal " + goal.getId() + ": " + goal.getTitle());
            System.out.println("Matching Subgoals:");

            List<String> subGoals = goal.getSubGoals();
            if (subGoals.isEmpty()) {
                System.out.println("  No matching subgoals found.");
            } else {
                for (String subGoal : subGoals) {
                    System.out.println("  - " + subGoal);
                }
            }
        }
    }
}
