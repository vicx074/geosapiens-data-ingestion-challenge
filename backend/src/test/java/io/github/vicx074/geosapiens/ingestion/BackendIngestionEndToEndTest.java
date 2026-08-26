package io.github.vicx074.geosapiens.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vicx074.geosapiens.ingestion.infrastructure.config.TemporaryStorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "app.worker.enabled=true",
    "app.worker.concurrency=1",
    "app.worker.prefetch=1",
    "app.worker.batch-size=2",
    "app.outbox.enabled=true",
    "app.outbox.poll-interval=100ms"
})
class BackendIngestionEndToEndTest {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(20);
  private static final Set<String> TERMINAL_STATUSES =
      Set.of("COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Container
  @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:4.3.5-alpine");

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private TemporaryStorageProperties storageProperties;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql(
        "TRUNCATE TABLE ingestion_errors, transactions, ingestion_outbox, ingestion_jobs")
        .update();
  }

  @Test
  void shouldProcessUploadAsynchronouslyAndExposeCommittedResultsThroughTheApi() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "transactions-e2e.csv",
        "text/csv",
        csvWithValidAndInvalidRows().getBytes(StandardCharsets.UTF_8));

    MvcResult upload = mvc.perform(multipart("/imports").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").isNotEmpty())
        .andReturn();

    JsonNode acceptedBody = objectMapper.readTree(upload.getResponse().getContentAsString());
    UUID jobId = UUID.fromString(acceptedBody.get("jobId").asText());
    assertThat(upload.getResponse().getHeader("Location")).isEqualTo("/imports/" + jobId);

    String terminalStatus = awaitTerminalStatus(jobId);
    assertThat(terminalStatus).isEqualTo("COMPLETED_WITH_ERRORS");
    awaitTemporaryFileCleanup(jobId);

    mvc.perform(get("/imports/{jobId}", jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
        .andExpect(jsonPath("$.processedRows").value(3))
        .andExpect(jsonPath("$.acceptedRows").value(2))
        .andExpect(jsonPath("$.rejectedRows").value(1))
        .andExpect(jsonPath("$.terminal").value(true));

    mvc.perform(get("/imports/{jobId}/transactions", jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sourceRow").value(2))
        .andExpect(jsonPath("$.items[0].transactionId").value("txn-e2e-001"))
        .andExpect(jsonPath("$.items[1].sourceRow").value(4))
        .andExpect(jsonPath("$.items[1].transactionId").value("txn-e2e-003"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());

    mvc.perform(get("/imports/{jobId}/errors", jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].sourceRow").value(3))
        .andExpect(jsonPath("$.items[0].code").value("AMOUNT_INVALID"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());

    mvc.perform(get("/imports/{jobId}/analytics", jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionCount").value(2))
        .andExpect(jsonPath("$.totalAmount").value(74.5))
        .andExpect(jsonPath("$.byCategory.length()").value(1))
        .andExpect(jsonPath("$.byCategory[0].category").value("Alimentação"))
        .andExpect(jsonPath("$.byCategory[0].transactionCount").value(2))
        .andExpect(jsonPath("$.byCategory[0].totalAmount").value(74.5))
        .andExpect(jsonPath("$.byMonth.length()").value(2))
        .andExpect(jsonPath("$.byMonth[0].month").value("2025-01"))
        .andExpect(jsonPath("$.byMonth[0].totalAmount").value(100.0))
        .andExpect(jsonPath("$.byMonth[1].month").value("2025-02"))
        .andExpect(jsonPath("$.byMonth[1].totalAmount").value(-25.5));

    String outboxStatus = jdbcClient.sql(
            "SELECT status FROM ingestion_outbox WHERE job_id = :jobId")
        .param("jobId", jobId)
        .query(String.class)
        .single();
    assertThat(outboxStatus).isEqualTo("PUBLISHED");
  }

  private String awaitTerminalStatus(UUID jobId) throws InterruptedException {
    Instant deadline = Instant.now().plus(ASYNC_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      String status = jdbcClient.sql(
              "SELECT status FROM ingestion_jobs WHERE id = :jobId")
          .param("jobId", jobId)
          .query(String.class)
          .optional()
          .orElse(null);
      if (status != null && TERMINAL_STATUSES.contains(status)) {
        return status;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("A importação não alcançou estado terminal dentro do tempo esperado.");
  }

  private void awaitTemporaryFileCleanup(UUID jobId) throws InterruptedException {
    Path temporaryFile = storageProperties.temporaryDirectory().resolve(jobId + ".csv");
    Instant deadline = Instant.now().plus(ASYNC_TIMEOUT);

    // O estado terminal é persistido antes do cleanup. A espera separada prova essa segunda etapa sem
    // pressupor que ambas se tornem observáveis exatamente no mesmo instante.
    while (Instant.now().isBefore(deadline)) {
      if (Files.notExists(temporaryFile)) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("O arquivo temporário não foi removido depois do processamento.");
  }

  private static String csvWithValidAndInvalidRows() {
    return """
        transaction_id,occurred_at,amount,category
        txn-e2e-001,2025-01-10T10:00:00Z,100.00,Alimentação
        txn-e2e-002,2025-01-11T10:00:00Z,valor-inválido,Transporte
        txn-e2e-003,2025-02-01T10:00:00Z,-25.50,Alimentação
        """;
  }
}
