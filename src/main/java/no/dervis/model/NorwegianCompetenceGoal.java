package no.dervis.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Norwegian-specific implementation of CompetenceGoal for pensum.json
 */
public class NorwegianCompetenceGoal extends CompetenceGoal {
    
    @JsonProperty("kompetansemål")
    @Override
    public void setId(int id) {
        super.setId(id);
    }
    
    @JsonProperty("tittel")
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
    }
    
    @JsonProperty("delmål")
    @Override
    public void setSubGoals(List<String> subGoals) {
        super.setSubGoals(subGoals);
    }
}