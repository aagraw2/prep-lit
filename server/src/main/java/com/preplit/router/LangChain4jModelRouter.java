package com.preplit.router;

import com.preplit.model.Message;
import com.preplit.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

@Component
public class LangChain4jModelRouter implements ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jModelRouter.class);

    private final StreamingChatLanguageModel chatModel;
    private final boolean devMode;

    public LangChain4jModelRouter(
            StreamingChatLanguageModel chatModel,
            @Value("${app.dev-mode:false}") boolean devMode) {
        this.chatModel = chatModel;
        this.devMode = devMode;
    }

    @Override
    public Flux<String> streamChat(List<Message> history, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // Skip leading assistant messages - API expects first message to be from user
        boolean foundFirstUser = false;
        for (Message msg : history) {
            if (!foundFirstUser && msg.getRole() == MessageRole.ASSISTANT) {
                continue; // Skip assistant messages before first user message
            }
            foundFirstUser = true;

            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AiMessage(msg.getContent()));
            }
        }

        // Log in dev mode
        if (devMode) {
            log.info("========== AI REQUEST ==========");
            log.info("System Prompt: {}", systemPrompt);
            log.info("Messages being sent to AI:");
            for (ChatMessage msg : messages) {
                log.info("  [{}]: {}", msg.getClass().getSimpleName(), msg);
            }
            log.info("=================================");
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullResponse = new StringBuilder();

        chatModel.generate(messages, new dev.langchain4j.model.StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                if (devMode) {
                    fullResponse.append(token);
                }
                sink.tryEmitNext(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                if (devMode) {
                    log.info("========== AI RESPONSE ==========");
                    log.info("Full response: {}", fullResponse.toString());
                    log.info("==================================");
                }
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("AI Error: {}", error.getMessage(), error);
                sink.tryEmitError(error);
            }
        });

        return sink.asFlux();
    }
}
