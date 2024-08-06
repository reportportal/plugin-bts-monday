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

import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.reportportal.extension.monday.model.graphql.GetItemsQuery;
import com.epam.reportportal.model.externalsystem.Ticket;
import com.epam.reportportal.rules.commons.validation.Suppliers;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.dao.IntegrationRepository;
import com.epam.ta.reportportal.dao.TicketRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class GetIssueCommand implements CommonPluginCommand<Ticket> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetIssueCommand.class);

  private final String TICKET_ID = "ticketId";
  private final String PROJECT_ID = "projectId";

  private final MondayClientProvider mondayClientProvider;

  private final TicketRepository ticketRepository;
  private final IntegrationRepository integrationRepository;

  public GetIssueCommand(MondayClientProvider mondayClientProvider,
      TicketRepository ticketRepository, IntegrationRepository integrationRepository) {
    this.mondayClientProvider = mondayClientProvider;
    this.ticketRepository = ticketRepository;
    this.integrationRepository = integrationRepository;
  }

  @Override
  public Ticket executeCommand(Map<String, Object> params) {
    final com.epam.ta.reportportal.entity.bts.Ticket ticket = ticketRepository.findByTicketId(
        (String) ofNullable(params.get(TICKET_ID)).orElseThrow(
            () -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
                TICKET_ID + " must be provided"
            ))).orElseThrow(() -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
        "Ticket not found with id " + TICKET_ID
    ));
    final Long projectId = (Long) ofNullable(params.get(PROJECT_ID)).orElseThrow(
        () -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
            PROJECT_ID + " must be provided"
        ));

    final String btsUrl = MondayProperties.URL.getParam(params);
    final String boardId = MondayProperties.PROJECT.getParam(params);

    final Integration integration =
        integrationRepository.findProjectBtsByUrlAndLinkedProject(btsUrl, boardId, projectId)
            .orElseGet(
                () -> integrationRepository.findGlobalBtsByUrlAndLinkedProject(btsUrl, boardId)
                    .orElseThrow(() -> new ReportPortalException(
                        ErrorType.BAD_REQUEST_ERROR,
                        "Integration with provided url and project isn't found"
                    )));
    return getTicket(ticket.getTicketId(), integration.getParams());
  }

  private Ticket getTicket(String id, IntegrationParams details) {

    String url = MondayProperties.URL.getParam(details.getParams());
    String boardId = MondayProperties.PROJECT.getParam(details);

    MondayClient mondayClient = mondayClientProvider.provide(details);

    return mondayClient.getItem(id).map(issue -> convertToTicket(url, boardId, issue)).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Ticket not found by id: " + id
        ));

  }

  private Ticket convertToTicket(String url, String boardId, GetItemsQuery.Item issue) {
    Ticket ticket = new Ticket();
    ticket.setId(issue.id);
    ticket.setSummary(issue.name);
    ticket.setStatus("");
    ofNullable(issue.column_values).flatMap(
            values -> values.stream().filter(v -> Objects.nonNull(v.onStatusValue))
                .map(v -> v.onStatusValue).filter(s -> Objects.nonNull(s.id))
                .filter(s -> s.id.contains("status")).findFirst())
        .ifPresentOrElse(s -> ticket.setStatus(s.text), () -> ticket.setStatus(""));
    ticket.setTicketUrl(
        Suppliers.formattedSupplier("{}/boards/{}/pulses/{}", url, boardId, issue.id).get());
    return ticket;
  }

  @Override
  public String getName() {
    return "getIssue";
  }
}
