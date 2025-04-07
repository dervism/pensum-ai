package no.dervis;

import dev.langchain4j.model.github.GitHubModelsChatModel;

import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;

public class GHModels {

    public static void main(String[] args) {
        GitHubModelsChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GH_TOKEN"))
                .modelName(GPT_4_O_MINI)
                .logRequestsAndResponses(false)
                .build();

        String response = model.chat("Provide 3 short bullet points explaining the Ottoman Empire.");

        System.out.println(response);
    }
}
