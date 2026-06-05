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

import static com.epam.reportportal.base.infrastructure.rules.commons.validation.BusinessRule.expect;
import static com.epam.reportportal.extension.monday.utils.ParamUtils.normalizeUrl;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepositoryCustom;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.google.common.collect.Maps;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class RetrieveCreationParamsCommand extends AbstractExtensionCommand<Map<String, Object>> {

  private final BasicTextEncryptor textEncryptor;

  public RetrieveCreationParamsCommand(BasicTextEncryptor textEncryptor,
      ProjectRepository projectRepository, OrganizationRepositoryCustom organizationRepository) {
    super(projectRepository, organizationRepository);
    this.textEncryptor = textEncryptor;

    // Set required permission levels
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "retrieveCreate";
  }

  @Override
  public Map<String, Object> executeCommand(PluginCommandRQ pluginCommandRq) {
    var integrationParams = pluginCommandRq.getArguments();
    expect(integrationParams, MapUtils::isNotEmpty).verify(
        ErrorType.BAD_REQUEST_ERROR, "No integration params provided");

    Map<String, Object> resultParams =
        Maps.newHashMapWithExpectedSize(MondayProperties.values().length);

    resultParams.put(MondayProperties.URL.getName(),
        normalizeUrl(MondayProperties.URL.getParam(integrationParams)));
    resultParams.put(
        MondayProperties.PROJECT.getName(), MondayProperties.PROJECT.getParam(integrationParams));
    resultParams.put(MondayProperties.API_TOKEN.getName(),
        textEncryptor.encrypt(MondayProperties.API_TOKEN.getParam(integrationParams))
    );

    return resultParams;
  }

}
