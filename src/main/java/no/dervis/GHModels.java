package no.dervis;

import dev.langchain4j.model.openai.OpenAiChatModel;

public class GHModels {

    public static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .apiKey(System.getenv("GH_TOKEN"))
                .modelName("gpt-4o-mini")
                .logRequests(false)
                .logResponses(false)
                .build();

        String response = model.chat("Provide 3 short bullet points explaining the Ottoman Empire.");

        System.out.println(response);
    }
}
