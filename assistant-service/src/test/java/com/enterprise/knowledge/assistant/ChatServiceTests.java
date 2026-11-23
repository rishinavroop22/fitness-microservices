package com.enterprise.knowledge.assistant;

import com.enterprise.knowledge.assistant.agent.AgentClient;
import com.enterprise.knowledge.assistant.agent.AgentRequest;
import com.enterprise.knowledge.assistant.agent.AgentResponse;
import com.enterprise.knowledge.assistant.dto.ExchangeResponse;
import com.enterprise.knowledge.assistant.service.ChatService;
import com.enterprise.knowledge.assistant.service.ConversationNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTests {

    @Autowired
    private ChatService chatService;

    @MockitoBean
    private AgentClient agentClient;

    private AgentResponse answer(String text) {
        return new AgentResponse(text, "knowledge", "q",
                List.of(Map.of("number", 1, "documentId", "doc-1", "title", "Leave Policy")),
                List.of(), false, List.of(Map.of("node", "retrieve", "hits", 3)));
    }

    @Test
    void firstQuestionCreatesConversationAndStoresCitations() {
        when(agentClient.ask(any())).thenReturn(answer("You get 24 days [1]."));

        ExchangeResponse exchange = chatService.ask(null, "How many leave days do I get?", "u1", "HR");

        assertThat(exchange.conversation().title()).isEqualTo("How many leave days do I get?");
        assertThat(exchange.answer().content()).isEqualTo("You get 24 days [1].");
        assertThat(exchange.answer().citations()).hasSize(1);
        assertThat(chatService.listMessages(exchange.conversation().id(), "u1")).hasSize(2);
    }

    @Test
    void followUpSendsHistoryAndDepartmentToTheAgent() {
        when(agentClient.ask(any())).thenReturn(answer("first"), answer("second"));
        String conversationId = chatService.ask(null, "Tell me about annual leave", "u2", "ENGINEERING")
                .conversation().id();

        chatService.ask(conversationId, "Can I carry it forward?", "u2", "ENGINEERING");

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentClient, times(2)).ask(captor.capture());
        AgentRequest followUp = captor.getAllValues().get(1);
        assertThat(followUp.department()).isEqualTo("ENGINEERING");
        assertThat(followUp.history()).extracting(AgentRequest.Turn::role).containsExactly("user", "assistant");
        assertThat(followUp.history().get(0).content()).isEqualTo("Tell me about annual leave");
    }

    @Test
    void usersCannotReadOtherUsersConversations() {
        when(agentClient.ask(any())).thenReturn(answer("hi"));
        String id = chatService.ask(null, "hello", "owner", "ALL").conversation().id();

        assertThatThrownBy(() -> chatService.listMessages(id, "intruder"))
                .isInstanceOf(ConversationNotFoundException.class);
    }
}
