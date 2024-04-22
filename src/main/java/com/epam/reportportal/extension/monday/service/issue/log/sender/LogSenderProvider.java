package com.epam.reportportal.extension.monday.service.issue.log.sender;

import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.ta.reportportal.binary.DataStoreService;

public class LogSenderProvider {

  private final DataStoreService dataStoreService;

  public LogSenderProvider(DataStoreService dataStoreService) {
    this.dataStoreService = dataStoreService;
  }

  public LogSender provide(MondayClient mondayClient) {
    return new LogSender(mondayClient, dataStoreService);
  }
}
