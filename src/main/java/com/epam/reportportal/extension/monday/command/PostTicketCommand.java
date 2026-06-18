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

import static com.epam.reportportal.base.infrastructure.persistence.commons.Predicates.isNull;
import static com.epam.reportportal.base.infrastructure.rules.commons.validation.BusinessRule.expect;
import static com.epam.reportportal.extension.util.CommandParamUtils.ENTITY_PARAM;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.PostFormField;
import com.epam.reportportal.base.infrastructure.model.externalsystem.PostTicketRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.Ticket;
import com.epam.reportportal.base.infrastructure.persistence.dao.LogRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TestItemRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.log.Log;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.commons.validation.Suppliers;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Slf4j
public class PostTicketCommand extends AbstractExtensionCommand<Ticket> {

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
      TestItemRepository testItemRepository, LogRepository logRepository,
      OrganizationUserRepository organizationUserRepository, OrganizationRepository organizationRepository,
      ProjectUserRepository projectUserRepository) {
    super(projectRepository, organizationUserRepository, organizationRepository, projectUserRepository);
    this.requestEntityConverter = requestEntityConverter;
    this.mondayClientProvider = mondayClientProvider;
    this.issueParamsConverter = issueParamsConverter;
    this.issueDescriptionProvider = issueDescriptionProvider;
    this.logSenderProvider = logSenderProvider;
    this.objectMapper = objectMapper;
    this.testItemRepository = testItemRepository;
    this.logRepository = logRepository;

    // Set required permission levels
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "postTicket";
  }

  @Override
  protected Ticket invokeCommand(Integration integration, PluginCommandRQ pluginCommandRq) {
    var params = pluginCommandRq.getArguments();
    PostTicketRQ ticketRQ = requestEntityConverter.getEntity(ENTITY_PARAM, params, PostTicketRQ.class);
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
        Suppliers.formattedSupplier("{}/boards/{}/pulses/{}", url, boardId, issueId).get());
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
      log.error(e.getMessage(), e);
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


}
