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

import static com.epam.reportportal.extension.monday.utils.ParamUtils.normalizeUrl;
import static com.epam.reportportal.rules.commons.validation.BusinessRule.expect;

import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.monday.model.enums.MondayProperties;
import com.epam.reportportal.rules.exception.ErrorType;
import com.google.common.collect.Maps;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class RetrieveCreationParamsCommand implements CommonPluginCommand<Map<String, Object>> {

  private final BasicTextEncryptor textEncryptor;

  public RetrieveCreationParamsCommand(BasicTextEncryptor textEncryptor) {
    this.textEncryptor = textEncryptor;
  }

  @Override
  public String getName() {
    return "retrieveCreate";
  }

  @Override
  public Map<String, Object> executeCommand(Map<String, Object> integrationParams) {

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
