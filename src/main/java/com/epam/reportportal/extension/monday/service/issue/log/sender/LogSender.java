package com.epam.reportportal.extension.monday.service.issue.log.sender;

import static com.epam.reportportal.rules.commons.validation.Suppliers.formattedSupplier;
import static com.epam.ta.reportportal.commons.EntityUtils.INSTANT_TO_LDT;
import static com.epam.ta.reportportal.commons.EntityUtils.TO_DATE;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.command.PostTicketCommand;
import com.epam.reportportal.model.externalsystem.PostTicketRQ;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.binary.DataStoreService;
import com.epam.ta.reportportal.entity.attachment.Attachment;
import com.epam.ta.reportportal.entity.log.Log;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogSender {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostTicketCommand.class);

  private static final String TEST_EXECUTION_LOG_TITLE = "Test execution log:";

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

  private final MondayClient mondayClient;
  private final DataStoreService dataStoreService;

  public LogSender(MondayClient mondayClient, DataStoreService dataStoreService) {
    this.mondayClient = mondayClient;
    this.dataStoreService = dataStoreService;
  }

  public void send(List<Log> logs, PostTicketRQ ticketRQ, String issueId, String sectionId) {
    StringBuilder descriptionBuilder = new StringBuilder();
    descriptionBuilder.append(
        formattedSupplier("<p><b>{}</b></p>", TEST_EXECUTION_LOG_TITLE).get());
    logs.forEach(log -> {
      if (ticketRQ.getIsIncludeLogs()) {
        descriptionBuilder.append("<p>").append(getFormattedMessage(log)).append("</p>");
      }
      if (ticketRQ.getIsIncludeScreenshots()) {
        ofNullable(log.getAttachment()).ifPresent(attachment -> {
          sendWithAttachment(issueId, sectionId, descriptionBuilder.toString(), attachment);
          descriptionBuilder.setLength(0);
        });
      }

    });

    if (descriptionBuilder.length() > 0) {
      send(issueId, sectionId, descriptionBuilder.toString());
    }
  }

  private String getFormattedMessage(Log log) {
    StringBuilder messageBuilder = new StringBuilder();
    ofNullable(log.getLogTime()).ifPresent(logTime -> messageBuilder.append(" Time: ")
        .append(DATE_FORMAT.format(INSTANT_TO_LDT.apply(logTime))).append(", "));
    ofNullable(log.getLogLevel()).ifPresent(
        logLevel -> messageBuilder.append("Level: ").append(logLevel).append(", "));
    messageBuilder.append("Log: ").append(log.getLogMessage());
    return messageBuilder.toString();
  }

  private void send(String issueId, String sectionId, String body) {
    mondayClient.createReply(issueId, sectionId, body).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Unable to create Log in Reply"
        ));
  }

  private void sendWithAttachment(String issueId, String sectionId, String body,
      Attachment attachment) {
    mondayClient.createReply(issueId, sectionId, body).map(r -> r.id)
        .ifPresent(replyId -> uploadAttachment(replyId, mondayClient, attachment));
  }

  private void uploadAttachment(String sectionId, MondayClient mondayClient,
      Attachment attachment) {
    Optional<InputStream> inputStreamOpt = dataStoreService.load(attachment.getFileId());
    if (inputStreamOpt.isPresent()) {
      try (InputStream inputStream = inputStreamOpt.get()) {
        mondayClient.uploadFile(
            sectionId, inputStream, attachment.getFileName(), attachment.getContentType());
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
        throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e.getMessage());
      }
    }
  }
}
