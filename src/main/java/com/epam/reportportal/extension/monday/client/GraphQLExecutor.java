package com.epam.reportportal.extension.monday.client;

import static java.util.Optional.ofNullable;

import com.apollographql.apollo3.ApolloClient;
import com.apollographql.apollo3.api.ApolloResponse;
import com.apollographql.apollo3.api.Mutation;
import com.apollographql.apollo3.api.Query;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import java.io.Closeable;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
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
      ApolloResponse<T> response = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
          (s, c) -> apolloClient.query(query).addHttpHeader(AUTHORIZATION_HEADER, token)
              .addHttpHeader(API_VERSION_HEADER, API_VERSION_VALUE).execute(c)
      );
      return ofNullable(response.data).orElseThrow(() -> {
        LOGGER.error("Response errors {}", response.errors);
        return new ReportPortalException(
            ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "No result for query: " + query.name());
      });
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e.getMessage());
    }
  }

  public <T extends Mutation.Data> T mutation(Mutation<T> mutation, String token) {
    try {
      ApolloResponse<T> response = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
          (s, c) -> apolloClient.mutation(mutation).addHttpHeader(AUTHORIZATION_HEADER, token)
              .addHttpHeader(API_VERSION_HEADER, API_VERSION_VALUE).execute(c)
      );
      return ofNullable(response.data).orElseThrow(() -> {
        LOGGER.error("Response errors {}", response.errors);
        return new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "No result for mutation: " + mutation.name()
        );
      });
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e.getMessage());
    }
  }

  @Override
  public void close() {
    apolloClient.close();
  }
}
