package com.mykb.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthKnowledgeBaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void registerLoginAndKnowledgeBaseShareFlowWorks() throws Exception {
    AuthContext alice = register("alice", "alice@example.com");
    AuthContext bob = register("bob", "bob@example.com");

    AuthContext aliceLogin = login("alice@example.com");
    String knowledgeBaseId = createKnowledgeBase(aliceLogin.token(), "alice-kb");

    shareKnowledgeBase(aliceLogin.token(), knowledgeBaseId, bob.email());

    mockMvc
        .perform(get("/api/v1/knowledge-bases").header("Authorization", "Bearer " + bob.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].name").value("alice-kb"))
        .andExpect(jsonPath("$.data[0].accessType").value("SHARED_VIEWER"));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + alice.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("alice"));
  }

  @Test
  void duplicateRegistrationShouldReturnConflict() throws Exception {
    register("repeat", "repeat@example.com");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "username", "repeat",
                            "email", "another@example.com",
                            "password", "Password123!"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
  }

  @Test
  void duplicateKnowledgeBaseNameShouldReturnConflict() throws Exception {
    AuthContext alice = register("alice-kb", "alice-kb@example.com");
    createKnowledgeBase(alice.token(), "shared-name");

    mockMvc
        .perform(
            post("/api/v1/knowledge-bases")
                .header("Authorization", "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "shared-name", "description", "second kb"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_EXISTS"))
        .andExpect(jsonPath("$.message").value("知识库名称已存在"));
  }

  @Test
  void shareToSelfShouldBeRejected() throws Exception {
    AuthContext alice = register("alice-self", "alice-self@example.com");
    String knowledgeBaseId = createKnowledgeBase(alice.token(), "self-share-kb");

    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/shares", knowledgeBaseId)
                .header("Authorization", "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("targetEmail", "alice-self@example.com"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_SHARE_TARGET"))
        .andExpect(jsonPath("$.message").value("不能共享给自己"));
  }

  @Test
  void shareToUnknownUserShouldReturnNotFound() throws Exception {
    AuthContext alice = register("alice-missing", "alice-missing@example.com");
    String knowledgeBaseId = createKnowledgeBase(alice.token(), "missing-share-kb");

    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/shares", knowledgeBaseId)
                .header("Authorization", "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("targetEmail", "ghost@example.com"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TARGET_USER_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("目标用户不存在"));
  }

  private AuthContext register(String username, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "username", username,
                                "email", email,
                                "password", "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode data = readData(result);
    return new AuthContext(data.get("accessToken").asText(), email);
  }

  private AuthContext login(String identity) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("identity", identity, "password", "Password123!"))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = readData(result);
    return new AuthContext(data.get("accessToken").asText(), identity);
  }

  private String createKnowledgeBase(String token, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/knowledge-bases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", name, "description", "owner knowledge base"))))
            .andExpect(status().isCreated())
            .andReturn();

    return readData(result).get("id").asText();
  }

  private void shareKnowledgeBase(String token, String knowledgeBaseId, String targetEmail)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/knowledge-bases/{knowledgeBaseId}/shares", knowledgeBaseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetEmail", targetEmail))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.visibility").value("SHARED"));
  }

  private JsonNode readData(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
  }

  private record AuthContext(String token, String email) {}
}

