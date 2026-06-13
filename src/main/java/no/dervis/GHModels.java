package no.dervis;

import dev.langchain4j.model.openai.OpenAiChatModel;

public class GHModels {

    static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .apiKey(System.getenv("GH_TOKEN"))
                .modelName("gpt-5")
                .logRequests(false)
                .logResponses(false)
                .build();

        String response = model.chat("Provide 3 short bullet points explaining quantum computing.");

        System.out.println(response);
    }
}
