package com.epam.reportportal.extension.monday.info;

import com.epam.reportportal.infrastructure.persistence.entity.integration.IntegrationType;

public interface PluginInfoProvider {

  IntegrationType provide(IntegrationType integrationType);
}
