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

import com.epam.reportportal.extension.ProjectManagerCommand;
import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.reportportal.extension.monday.model.graphql.GetBoardConfigQuery;
import com.epam.reportportal.extension.monday.model.graphql.type.ColumnType;
import com.epam.reportportal.extension.monday.model.payload.BoardSettings;
import com.epam.reportportal.model.externalsystem.AllowedValue;
import com.epam.reportportal.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class GetIssueFieldsCommand extends ProjectManagerCommand<List<PostFormField>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(GetIssueFieldsCommand.class);

  //	TODO probably more
  private final Set<ColumnType> excludedColumnTypes =
      Set.of(ColumnType.item_id, ColumnType.file, ColumnType.last_updated);

  private final MondayClientProvider mondayClientProvider;
  private final ObjectMapper objectMapper;

  public GetIssueFieldsCommand(ProjectRepository projectRepository,
      MondayClientProvider mondayClientProvider, ObjectMapper objectMapper) {
    super(projectRepository);
    this.mondayClientProvider = mondayClientProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getName() {
    return "getIssueFields";
  }

  @Override
  protected List<PostFormField> invokeCommand(Integration integration, Map<String, Object> params) {
    String boardId = MondayProperties.PROJECT.getParam(integration.getParams());

    MondayClient mondayClient = mondayClientProvider.provide(integration.getParams());

    return mondayClient.getBoard(boardId).map(b -> {
      List<GetBoardConfigQuery.Column> columns = b.columns;
      return columns.stream().filter(c -> !excludedColumnTypes.contains(c.type)).map(c -> {
        List<AllowedValue> allowedValues = new ArrayList<>();
        if (ColumnType.status == c.type) {
          addStatusColumn(c, allowedValues);
        }

        return new PostFormField(
            c.id, c.title, c.type.rawValue, "name".equals(c.id), new ArrayList<>(), allowedValues);
      }).collect(Collectors.toList());
    }).orElseGet(Collections::emptyList);

  }

  private void addStatusColumn(GetBoardConfigQuery.Column c, List<AllowedValue> allowedValues) {
    try {
      BoardSettings boardSettings = objectMapper.readValue(c.settings_str, BoardSettings.class);
      ofNullable(boardSettings.getLabels()).ifPresent(
          labels -> labels.forEach((k, v) -> allowedValues.add(new AllowedValue(k, k + ": " + v))));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

}
