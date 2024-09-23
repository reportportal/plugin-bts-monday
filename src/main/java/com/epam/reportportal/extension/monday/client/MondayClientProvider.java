package com.epam.reportportal.extension.monday.client;

import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import okhttp3.OkHttpClient;
import org.jasypt.util.text.BasicTextEncryptor;

public class MondayClientProvider {

  private final String fileUploadUrl;
  private final OkHttpClient okHttpClient;

  private final BasicTextEncryptor textEncryptor;
  private final GraphQLExecutor graphQLExecutor;

  public MondayClientProvider(String fileUploadUrl, OkHttpClient okHttpClient,
      BasicTextEncryptor textEncryptor, GraphQLExecutor graphQLExecutor) {
    this.fileUploadUrl = fileUploadUrl;
    this.okHttpClient = okHttpClient;
    this.textEncryptor = textEncryptor;
    this.graphQLExecutor = graphQLExecutor;
  }

  public MondayClient provide(IntegrationParams params) {
    String token = textEncryptor.decrypt(MondayProperties.API_TOKEN.getParam(params));
    return new MondayClient(okHttpClient, graphQLExecutor, fileUploadUrl, token);
  }

}
