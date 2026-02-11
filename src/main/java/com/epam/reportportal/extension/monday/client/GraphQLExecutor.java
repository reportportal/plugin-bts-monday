package com.epam.reportportal.extension.monday.client;

import static java.util.Optional.ofNullable;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.api.Mutation;
import com.apollographql.apollo.api.Query;
import com.apollographql.java.client.ApolloClient;
import com.epam.reportportal.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.infrastructure.rules.exception.ReportPortalException;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLExecutor implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLExecutor.class);

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String API_VERSION_HEADER = "API-Version";

  private static final String API_VERSION_VALUE = "2024-04";

  private final ApolloClient apolloClient;

  public GraphQLExecutor(ApolloClient apolloClient) {
    this.apolloClient = apolloClient;
  }

  public <T extends Query.Data> T query(Query<T> query, String token) {
    try {
      CompletableFuture<ApolloResponse<T>> future = new CompletableFuture<>();
      apolloClient.query(query)
          .addHttpHeader(AUTHORIZATION_HEADER, token)
          .addHttpHeader(API_VERSION_HEADER, API_VERSION_VALUE)
          .enqueue(future::complete);

      ApolloResponse<T> response = future.get();
      return ofNullable(response.data).orElseThrow(() -> {
        LOGGER.error("Response errors {}", response.errors);
        return new ReportPortalException(
            ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "No result for query: " + query.name());
      });
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION);
    } catch (ExecutionException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e.getCause());
    }
  }

  public <T extends Mutation.Data> T mutation(Mutation<T> mutation, String token) {
    try {
      CompletableFuture<ApolloResponse<T>> future = new CompletableFuture<>();
      apolloClient.mutation(mutation)
          .addHttpHeader(AUTHORIZATION_HEADER, token)
          .addHttpHeader(API_VERSION_HEADER, API_VERSION_VALUE)
          .enqueue(future::complete);

      ApolloResponse<T> response = future.get();
      return ofNullable(response.data).orElseThrow(() -> {
        LOGGER.error("Response errors {}", response.errors);
        return new ReportPortalException(
            ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "No result for mutation");
      });
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION);
    } catch (ExecutionException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e.getCause());
    }
  }

  @Override
  public void close() {
    apolloClient.close();
  }
}
