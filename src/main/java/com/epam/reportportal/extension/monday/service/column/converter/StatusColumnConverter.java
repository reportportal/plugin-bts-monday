package com.epam.reportportal.extension.monday.service.column.converter;

import com.epam.reportportal.model.externalsystem.PostFormField;
import java.util.Optional;

public class StatusColumnConverter implements IssueColumnConverter {
  @Override
  public Optional<String> convert(PostFormField field) {
    return field.getValue().stream().findFirst().map(v -> v.split(": ")[1]);
  }
}
