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

package com.epam.reportportal.extension.monday.command.connection;

import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.util.Map;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class TestConnectionCommand implements PluginCommand<Boolean> {

  private final MondayClientProvider mondayClientProvider;

  public TestConnectionCommand(MondayClientProvider mondayClientProvider) {
    this.mondayClientProvider = mondayClientProvider;
  }

  @Override
  public String getName() {
    return "testConnection";
  }

  @Override
  public Boolean executeCommand(Integration integration, Map<String, Object> params) {
    IntegrationParams integrationParams = ofNullable(integration.getParams()).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Integration params are not specified."
        ));

    String boardId = MondayProperties.PROJECT.getParam(integrationParams);

    MondayClient mondayClient = mondayClientProvider.provide(integrationParams);

    return mondayClient.getBoard(boardId).map(b -> Boolean.TRUE).orElse(Boolean.FALSE);
  }
}