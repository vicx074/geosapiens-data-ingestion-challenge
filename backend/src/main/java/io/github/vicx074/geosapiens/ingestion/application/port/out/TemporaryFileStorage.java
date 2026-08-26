package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface TemporaryFileStorage {

  /**
   * Armazena o conteúdo progressivamente. O ciclo de vida do stream continua sob responsabilidade
   * do chamador para que o adaptador não encerre um recurso que não criou.
   */
  StoredTemporaryFile store(UUID jobId, InputStream content) throws IOException;

  /**
   * Abre o arquivo durável de um job para leitura progressiva. O chamador deve encerrar o stream.
   */
  InputStream open(UUID jobId) throws IOException;

  /**
   * Remove o arquivo associado ao job depois que o processamento alcança estado terminal durável.
   */
  void delete(UUID jobId) throws IOException;

  void delete(String storageKey) throws IOException;
}
