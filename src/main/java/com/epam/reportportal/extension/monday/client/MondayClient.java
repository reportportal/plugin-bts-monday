package com.epam.reportportal.extension.monday.client;

import static java.util.Optional.ofNullable;

import com.apollographql.apollo3.api.Optional;
import com.epam.reportportal.extension.monday.model.graphql.CreateIssueMutation;
import com.epam.reportportal.extension.monday.model.graphql.CreateIssueUpdateSectionMutation;
import com.epam.reportportal.extension.monday.model.graphql.GetBoardConfigQuery;
import com.epam.reportportal.extension.monday.model.graphql.GetItemsQuery;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpMethod;

public class MondayClient {

  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final OkHttpClient okHttpClient;
  private final GraphQLExecutor graphQLExecutor;

  private final String fileUploadUrl;
  private final String token;

  public MondayClient(OkHttpClient okHttpClient, GraphQLExecutor graphQLExecutor,
      String fileUploadUrl, String token) {
    this.okHttpClient = okHttpClient;
    this.graphQLExecutor = graphQLExecutor;
    this.fileUploadUrl = fileUploadUrl;
    this.token = token;
  }

  public java.util.Optional<GetBoardConfigQuery.Board> getBoard(String boardId) {
    GetBoardConfigQuery getBoardConfigQuery =
        new GetBoardConfigQuery(Optional.present(List.of(boardId)));
    GetBoardConfigQuery.Data data = graphQLExecutor.query(getBoardConfigQuery, token);
    return ofNullable(data.boards).flatMap(boards -> boards.stream().findFirst());
  }

  public java.util.Optional<CreateIssueMutation.Create_item> createItem(String boardId, String name,
      String columns) {
    CreateIssueMutation createIssueMutation = new CreateIssueMutation(boardId, name,
        com.apollographql.apollo3.api.Optional.present(columns)
    );
    CreateIssueMutation.Data data = graphQLExecutor.mutation(createIssueMutation, token);
    return ofNullable(data.create_item);
  }

  public java.util.Optional<CreateIssueUpdateSectionMutation.Create_update> createItemUpdateSection(
      String itemId, String body) {
    CreateIssueUpdateSectionMutation createMutation =
        new CreateIssueUpdateSectionMutation(itemId, Optional.absent(), body);
    CreateIssueUpdateSectionMutation.Data data = graphQLExecutor.mutation(createMutation, token);
    return ofNullable(data.create_update);
  }

  public java.util.Optional<CreateIssueUpdateSectionMutation.Create_update> createReply(
      String itemId, String parentId, String body) {
    CreateIssueUpdateSectionMutation createMutation =
        new CreateIssueUpdateSectionMutation(itemId, Optional.present(parentId), body);
    CreateIssueUpdateSectionMutation.Data data = graphQLExecutor.mutation(createMutation, token);
    return ofNullable(data.create_update);
  }

  /**
   * File upload made without Apollo client because of a bunch of unresolved errors that occur when UploadAdapter from GraphQL is used
   */
  public boolean uploadFile(String parentId, InputStream inputStream, String fileName,
      String contentType) throws IOException {
    RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("query",
            "mutation ($file: File!) {add_file_to_update (file: $file, update_id: " + parentId
                + ") {id}}"
        ).addFormDataPart("variables[file]", fileName,
            RequestBody.create(IOUtils.toByteArray(inputStream), MediaType.parse(contentType))
        ).build();
    Request request = new Request.Builder().url(fileUploadUrl).method(HttpMethod.POST.name(), body)
        .addHeader(AUTHORIZATION_HEADER, token).build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      return response.isSuccessful();
    }
  }

  public java.util.Optional<GetItemsQuery.Item> getItem(String itemId) {
    GetItemsQuery getItemsQuery = new GetItemsQuery(Optional.present(List.of(itemId)));
    GetItemsQuery.Data data = graphQLExecutor.query(getItemsQuery, token);

    return ofNullable(data.items).flatMap(items -> items.stream().findFirst());
  }

}
