package no.dervis.model;

import java.util.List;

/**
 * Represents a competence goal with its subgoals.
 */
public class CompetenceGoal {

    private int id;
    private String title;
    private List<String> subGoals;

    // Default constructor for Jackson
    public CompetenceGoal() {
    }

    public CompetenceGoal(int id, String title, List<String> subGoals) {
        this.id = id;
        this.title = title;
        this.subGoals = subGoals;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getSubGoals() {
        return subGoals;
    }

    public void setSubGoals(List<String> subGoals) {
        this.subGoals = subGoals;
    }

    @Override
    public String toString() {
        return "CompetenceGoal{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", subGoals=" + subGoals +
                '}';
    }
}
