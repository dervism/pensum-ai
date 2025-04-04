package no.dervis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.dervis.model.CompetenceGoal;
import no.dervis.model.EnglishCompetenceGoal;
import no.dervis.model.NorwegianCompetenceGoal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for loading and managing competence goals from JSON files.
 */
public class CompetenceGoalService {

    private final ObjectMapper objectMapper;

    public CompetenceGoalService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Loads English competence goals from curriculum.json
     * 
     * @return List of CompetenceGoal objects
     * @throws IOException if the file cannot be read
     */
    public List<CompetenceGoal> loadEnglishCompetenceGoals() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("curriculum.json")) {
            if (is == null) {
                throw new IOException("Could not find curriculum.json");
            }
            List<EnglishCompetenceGoal> englishGoals = objectMapper.readValue(is, 
                    new TypeReference<List<EnglishCompetenceGoal>>() {});
            return new ArrayList<>(englishGoals);
        }
    }

    /**
     * Loads Norwegian competence goals from pensum.json
     * 
     * @return List of CompetenceGoal objects
     * @throws IOException if the file cannot be read
     */
    public List<CompetenceGoal> loadNorwegianCompetenceGoals() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("pensum.json")) {
            if (is == null) {
                throw new IOException("Could not find pensum.json");
            }
            List<NorwegianCompetenceGoal> norwegianGoals = objectMapper.readValue(is, 
                    new TypeReference<List<NorwegianCompetenceGoal>>() {});
            return new ArrayList<>(norwegianGoals);
        }
    }

    /**
     * Loads competence goals based on the specified language.
     * 
     * @param language "en" for English, "no" for Norwegian
     * @return List of CompetenceGoal objects
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the language is not supported
     */
    public List<CompetenceGoal> loadCompetenceGoals(String language) throws IOException {
        return switch (language.toLowerCase()) {
            case "en" -> loadEnglishCompetenceGoals();
            case "no" -> loadNorwegianCompetenceGoals();
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }
}
