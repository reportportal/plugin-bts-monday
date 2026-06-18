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

import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.BAD_REQUEST_ERROR;
import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.IntegrationParams;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.epam.reportportal.extension.monday.client.MondayClient;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class TestConnectionCommand extends AbstractExtensionCommand<Boolean> {

  private final MondayClientProvider mondayClientProvider;

  public TestConnectionCommand(MondayClientProvider mondayClientProvider,
      ProjectRepository projectRepository, OrganizationUserRepository organizationUserRepository,
      OrganizationRepository organizationRepository, ProjectUserRepository projectUserRepository) {
    super(projectRepository, organizationUserRepository, organizationRepository, projectUserRepository);
    this.mondayClientProvider = mondayClientProvider;

    // Set required permission levels
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "testConnection";
  }

  @Override
  protected Boolean invokeCommand(Integration integration, PluginCommandRQ pluginCommandRq) {
    IntegrationParams integrationParams = ofNullable(integration.getParams())
        .orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
            "Integration params are not specified."
        ));

    String url = MondayProperties.URL.getParam(integrationParams);

    if (!url.startsWith("https://") || !url.contains(".monday.com")) {
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Invalid URL.");
    }

    String boardId = MondayProperties.PROJECT.getParam(integrationParams);

    verifyBoardId(boardId);

    MondayClient mondayClient = mondayClientProvider.provide(integrationParams);
    try {
      return mondayClient.getBoard(boardId)
          .map(b -> Boolean.TRUE)
          .orElseThrow(
              () -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Board with provided id {} not found",
                  boardId));
    } catch (ReportPortalException rpe) {
      throw rpe;
    } catch (Exception e) {
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION);
    }

  }

  private void verifyBoardId(String boardId) {
    try {
      Long.parseLong(boardId);
    } catch (NumberFormatException e) {
      throw new ReportPortalException(BAD_REQUEST_ERROR, "Invalid Board ID");
    }
  }
}
