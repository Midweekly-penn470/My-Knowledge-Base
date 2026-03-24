package com.mykb.server.dify.client;

import java.util.List;
import java.util.function.Consumer;

public interface DifyClient {

  DifyDatasetResult createDataset(String name, String description);

  DifyDocumentUploadResult uploadDocument(
      String datasetId, String filename, String contentType, byte[] fileBytes);

  DifyDocumentUploadResult createTextDocument(String datasetId, String name, String text);

  DifyIndexingStatusResult getIndexingStatus(String datasetId, String batchId);

  default List<DifyRetrievedChunk> retrieveChunks(String datasetId, String query) {
    throw new UnsupportedOperationException("retrieveChunks is not implemented");
  }

  default void streamCompletion(
      DifyCompletionRequest request, Consumer<DifyCompletionEvent> eventConsumer) {
    throw new UnsupportedOperationException("streamCompletion is not implemented");
  }
}
