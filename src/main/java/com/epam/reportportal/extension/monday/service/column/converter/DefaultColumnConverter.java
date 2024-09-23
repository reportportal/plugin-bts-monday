package com.epam.reportportal.extension.monday.service.column.converter;

import com.epam.reportportal.model.externalsystem.PostFormField;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class DefaultColumnConverter implements IssueColumnConverter {
  @Override
  public Optional<String> convert(PostFormField field) {
    return field.getValue().stream().filter(StringUtils::isNotBlank).findFirst();
  }
}
