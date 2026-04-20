package com.preplit.router;

import com.preplit.model.Message;
import reactor.core.publisher.Flux;
import java.util.List;

public interface ModelRouter {
    Flux<String> streamChat(List<Message> history, String systemPrompt);
}
