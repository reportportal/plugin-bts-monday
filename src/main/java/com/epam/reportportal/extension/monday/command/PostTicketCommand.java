/*
 * Copyright 2021 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.monday.command;

import static com.epam.reportportal.extension.util.CommandParamUtils.ENTITY_PARAM;
import static com.epam.reportportal.rules.commons.validation.BusinessRule.expect;
import static com.epam.ta.reportportal.commons.Predicates.isNull;
import static com.epam.ta.reportportal.commons.Predicates.not;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.ProjectMemberCommand;
import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.model.enums.MondayColumnId;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.reportportal.extension.monday.service.issue.IssueDescriptionProvider;
import com.epam.reportportal.extension.monday.service.issue.converter.IssueParamsConverter;
import com.epam.reportportal.extension.monday.service.issue.log.sender.LogSender;
import com.epam.reportportal.extension.monday.service.issue.log.sender.LogSenderProvider;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.epam.reportportal.extension.util.RequestEntityValidator;
import com.epam.reportportal.model.externalsystem.PostFormField;
import com.epam.reportportal.model.externalsystem.PostTicketRQ;
import com.epam.reportportal.model.externalsystem.Ticket;
import com.epam.reportportal.rules.commons.validation.Suppliers;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.log.Log;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class PostTicketCommand extends ProjectMemberCommand<Ticket> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostTicketCommand.class);

  private final RequestEntityConverter requestEntityConverter;

  private final MondayClientProvider mondayClientProvider;

  private final IssueParamsConverter issueParamsConverter;
  private final IssueDescriptionProvider issueDescriptionProvider;

  private final LogSenderProvider logSenderProvider;

  private final ObjectMapper objectMapper;

  private final TestItemRepository testItemRepository;
  private final LogRepository logRepository;

  public PostTicketCommand(ProjectRepository projectRepository,
      RequestEntityConverter requestEntityConverter, MondayClientProvider mondayClientProvider,
      IssueParamsConverter issueParamsConverter, IssueDescriptionProvider issueDescriptionProvider,
      LogSenderProvider logSenderProvider, ObjectMapper objectMapper,
      TestItemRepository testItemRepository, LogRepository logRepository) {
    super(projectRepository);
    this.requestEntityConverter = requestEntityConverter;
    this.mondayClientProvider = mondayClientProvider;
    this.issueParamsConverter = issueParamsConverter;
    this.issueDescriptionProvider = issueDescriptionProvider;
    this.logSenderProvider = logSenderProvider;
    this.objectMapper = objectMapper;
    this.testItemRepository = testItemRepository;
    this.logRepository = logRepository;
  }

  @Override
  public String getName() {
    return "postTicket";
  }

  @Override
  protected Ticket invokeCommand(Integration integration, Map<String, Object> params) {
    PostTicketRQ ticketRQ =
        requestEntityConverter.getEntity(ENTITY_PARAM, params, PostTicketRQ.class);
    RequestEntityValidator.validate(ticketRQ);
    expect(ticketRQ.getFields(), not(isNull())).verify(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
        "External System fields set is empty!"
    );
    List<PostFormField> fields = ticketRQ.getFields();

    String name = getIssueName(fields);

    String boardId = MondayProperties.PROJECT.getParam(integration.getParams());
    String url = MondayProperties.URL.getParam(integration.getParams());
    String columns = convertToIssueColumns(fields);

    MondayClient mondayClient = mondayClientProvider.provide(integration.getParams());

    String issueId = mondayClient.createItem(boardId, name, columns).map(i -> i.id).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Error during issue creation"
        ));

    ofNullable(ticketRQ.getBackLinks()).filter(m -> !m.isEmpty())
        .ifPresent(backLinks -> postBackLinks(ticketRQ, mondayClient, issueId, backLinks));

    Ticket ticket = new Ticket();
    ticket.setId(issueId);
    ticket.setSummary(name);
    ticket.setTicketUrl(
        Suppliers.formattedSupplier("{}/boards/{}/pulses/{}", removeTrailingSlash(url), boardId,
            issueId).get());

    return ticket;
  }

  private String getIssueName(List<PostFormField> fields) {
    return fields.stream().filter(f -> MondayColumnId.NAME.matches(f.getId()))
        .filter(f -> CollectionUtils.isNotEmpty(f.getValue()))
        .filter(f -> f.getValue().stream().findFirst().isPresent()).findFirst()
        .flatMap(f -> f.getValue().stream().findFirst()).orElseThrow(
            () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                "Issue name not provided"
            ));
  }

  private String convertToIssueColumns(List<PostFormField> fields) {
    Map<String, String> issueParams = issueParamsConverter.convert(fields);
    return writeAsString(issueParams);
  }

  private String writeAsString(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
          "Unable to convert columns: " + e.getMessage()
      );
    }
  }

  private void postBackLinks(PostTicketRQ ticketRQ, MondayClient mondayClient, String issueId,
      Map<Long, String> backLinks) {
    backLinks.forEach((id, link) -> testItemRepository.findById(id).ifPresent(item -> {
      String mainUpdateBody = issueDescriptionProvider.provide(ticketRQ, item, link);
      String sectionId =
          mondayClient.createItemUpdateSection(issueId, mainUpdateBody).map(s -> s.id)
              .orElseThrow(() -> new ReportPortalException(
                  ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                  "Error during description creation for item: " + item.getItemId()
              ));
      if (!ticketRQ.getIsIncludeLogs() && !ticketRQ.getIsIncludeScreenshots()) {
        return;
      }
      ofNullable(item.getLaunchId()).ifPresent(launchId -> {
        List<Log> logs =
            logRepository.findAllUnderTestItemByLaunchIdAndTestItemIdsWithLimit(launchId,
                List.of(item.getItemId()), ticketRQ.getNumberOfLogs()
            );
        if (logs.isEmpty()) {
          return;
        }
        LogSender logSender = logSenderProvider.provide(mondayClient);
        logSender.send(logs, ticketRQ, issueId, sectionId);
      });
    }));
  }

  private String removeTrailingSlash(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }

}
