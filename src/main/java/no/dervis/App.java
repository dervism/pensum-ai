package no.dervis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.github.GitHubModelsChatModelName;
import no.dervis.model.CompetenceGoal;
import no.dervis.service.CompetenceGoalService;
import no.dervis.service.LlmService;
import no.dervis.service.LlmService.LlmProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

/**
 * Main application class for the Competence Goal Matcher.
 * This application reads competence goals from a JSON file, asks the developer about their tasks,
 * and matches the developer's response to competence goals using an LLM.
 */
public class App {

    // Configuration constants
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String OLLAMA_ENDPOINT = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_MODEL = "qwen2.5-coder:32b";
    private static final GitHubModelsChatModelName DEFAULT_GITHUB_MODEL = GitHubModelsChatModelName.GPT_4_O_MINI;

    // Services
    private final CompetenceGoalService competenceGoalService;
    private final LlmService llmService;
    private final BufferedReader inputReader;

    /**
     * Creates a new App instance with the specified services.
     *
     * @param competenceGoalService Service for loading competence goals
     * @param llmService Service for matching developer responses to competence goals
     * @param inputReader Reader for user input
     */
    public App(CompetenceGoalService competenceGoalService, LlmService llmService, BufferedReader inputReader) {
        this.competenceGoalService = competenceGoalService;
        this.llmService = llmService;
        this.inputReader = inputReader;
    }

    /**
     * Main entry point of the application.
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            CommandLineOptions options = parseCommandLineArgs(args);

            // Initialize services
            ObjectMapper objectMapper = new ObjectMapper();
            CompetenceGoalService goalService = new CompetenceGoalService(objectMapper);

            // Create LLM service based on selected provider
            LlmService llmService = createLlmService(objectMapper, options);

            // Create and run the application
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            App app = new App(goalService, llmService, reader);
            app.run(options.language());

        } catch (Exception e) {
            System.err.println("Error running application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Runs the application with the specified language.
     *
     * @param language The language code for competence goals
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the matching process is interrupted
     */
    public void run(String language) throws IOException, InterruptedException {
        // Load competence goals
        List<CompetenceGoal> competenceGoals = loadCompetenceGoals(language);
        System.out.println("Loaded " + competenceGoals.size() + " competence goals.");

        // Ask developer about their tasks
        String developerResponse = askDeveloper();

        // Match developer response to competence goals
        System.out.println("\nMatching your response to competence goals...");
        List<CompetenceGoal> matchingGoals = llmService.matchCompetenceGoals(developerResponse, competenceGoals);

        // Display matching goals
        displayMatchingGoals(matchingGoals);
    }

    /**
     * Loads competence goals from the JSON file based on the specified language.
     *
     * @param language The language code for competence goals
     * @return A list of competence goals
     * @throws IOException If an I/O error occurs
     */
    private List<CompetenceGoal> loadCompetenceGoals(String language) throws IOException {
        return competenceGoalService.loadCompetenceGoals(language);
    }

    /**
     * Asks the developer about their tasks and returns their response.
     *
     * @return The developer's response
     * @throws IOException If an I/O error occurs
     */
    private String askDeveloper() throws IOException {
        System.out.println("\nPlease describe the tasks you performed in your recent work:");
        System.out.println("(Type your response and press Enter, then type 'done' on a new line to finish)");

        StringBuilder response = new StringBuilder();
        String line;

        while (!(line = inputReader.readLine()).equalsIgnoreCase("done")) {
            response.append(line).append("\n");
        }

        return response.toString().trim();
    }

    /**
     * Displays the matching competence goals and their subgoals.
     *
     * @param matchingGoals The list of matching competence goals
     */
    private void displayMatchingGoals(List<CompetenceGoal> matchingGoals) {
        if (matchingGoals.isEmpty()) {
            System.out.println("\nNo matching competence goals found.");
            return;
        }

        System.out.println("\nMatching Competence Goals:");
        System.out.println("==========================");

        for (CompetenceGoal goal : matchingGoals) {
            System.out.println("Goal " + goal.getId() + ": " + goal.getTitle());

            List<String> subGoals = goal.getSubGoals();
            if (!subGoals.isEmpty()) {
                System.out.println("Matching subgoals:");
                for (String subGoal : subGoals) {
                    System.out.println("  • " + subGoal);
                }
            } else {
                System.out.println("No specific subgoals matched.");
            }

            System.out.println();
        }
    }

    /**
     * Creates an LLM service based on the specified command line options.
     *
     * @param objectMapper The ObjectMapper for JSON serialization/deserialization
     * @param options Command line options
     * @return A configured LLM service
     */
    private static LlmService createLlmService(ObjectMapper objectMapper, CommandLineOptions options) {
        return switch (options.provider()) {
            case OLLAMA -> new LlmService(objectMapper, OLLAMA_ENDPOINT, options.ollamaModel().orElse(DEFAULT_OLLAMA_MODEL));
            case GITHUB_MODELS -> new LlmService(objectMapper, options.githubModel().orElse(DEFAULT_GITHUB_MODEL));
        };
    }

    /**
     * Parses command line arguments.
     *
     * @param args Command line arguments
     * @return Parsed command line options
     */
    private static CommandLineOptions parseCommandLineArgs(String[] args) {
        String language = DEFAULT_LANGUAGE;
        LlmProvider provider = LlmProvider.GITHUB_MODELS;
        Optional<String> ollamaModel = Optional.empty();
        Optional<GitHubModelsChatModelName> githubModel = Optional.empty();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--language", "-l" -> {
                    if (i + 1 < args.length) {
                        language = args[++i];
                    }
                }
                case "--provider", "-p" -> {
                    if (i + 1 < args.length) {
                        String providerArg = args[++i].toUpperCase();
                        try {
                            provider = LlmProvider.valueOf(providerArg);
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid provider: " + providerArg);
                            System.err.println("Using default provider: " + provider);
                        }
                    }
                }
                case "--ollama-model", "-om" -> {
                    if (i + 1 < args.length) {
                        ollamaModel = Optional.of(args[++i]);
                    }
                }
                case "--github-model", "-gm" -> {
                    if (i + 1 < args.length) {
                        try {
                            githubModel = Optional.of(GitHubModelsChatModelName.valueOf(args[++i]));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid GitHub model: " + args[i]);
                            System.err.println("Using default model: " + DEFAULT_GITHUB_MODEL);
                        }
                    }
                }
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(0);
                }
            }
        }

        return new CommandLineOptions(language, provider, ollamaModel, githubModel);
    }

    /**
     * Prints help information.
     */
    private static void printHelp() {
        System.out.println("""
            Competence Goal Matcher
            
            Usage:
              java -jar competence-goal-matcher.jar [options]
              
            Options:
              -l, --language <code>       Language code for competence goals (default: en)
              -p, --provider <provider>   LLM provider to use (OLLAMA or GITHUB_MODELS, default: OLLAMA)
              -om, --ollama-model <model> Ollama model to use (default: llama3)
              -gm, --github-model <model> GitHub model to use (default: GPT_4_O_MINI)
              -h, --help                  Show this help message
            """);
    }

    /**
     * Record representing parsed command line options.
     */
    private record CommandLineOptions(
            String language,
            LlmProvider provider,
            Optional<String> ollamaModel,
            Optional<GitHubModelsChatModelName> githubModel
    ) {}
}