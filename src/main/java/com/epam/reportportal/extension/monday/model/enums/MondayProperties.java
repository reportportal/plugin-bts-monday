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

package com.epam.reportportal.extension.monday.model.enums;

import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public enum MondayProperties {

  URL("url"), PROJECT("project"), API_TOKEN("apiToken");

  private final String name;

  MondayProperties(String name) {
    this.name = name;
  }

  public Optional<String> findParam(Map<String, Object> params) {
    return Optional.ofNullable(params.get(this.name)).map(String::valueOf);
  }

  public String getParam(Map<String, Object> params) {
    return findParam(params).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "BTS parameter not provided: " + this.name
        ));
  }

  public Optional<String> findParam(IntegrationParams params) {
    return Optional.ofNullable(params.getParams().get(this.name)).map(o -> (String) o);
  }

  public String getParam(IntegrationParams params) {
    return findParam(params).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "BTS parameter not provided: " + this.name
        ));
  }

  public void setParam(IntegrationParams params, String value) {
    if (null == params.getParams()) {
      params.setParams(new HashMap<>());
    }
    params.getParams().put(this.name, value);
  }

  public String getName() {
    return name;
  }
}
