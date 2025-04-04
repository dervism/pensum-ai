package no.dervis.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * English-specific implementation of CompetenceGoal for curriculum.json
 */
public class EnglishCompetenceGoal extends CompetenceGoal {
    
    @JsonProperty("competenceGoal")
    @Override
    public void setId(int id) {
        super.setId(id);
    }
    
    @JsonProperty("competenceGoalTitle")
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
    }
    
    @JsonProperty("subGoals")
    @Override
    public void setSubGoals(List<String> subGoals) {
        super.setSubGoals(subGoals);
    }
}