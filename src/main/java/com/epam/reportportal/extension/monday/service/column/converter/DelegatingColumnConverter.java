package com.epam.reportportal.extension.monday.service.column.converter;

import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.monday.model.enums.MondayColumnType;
import com.epam.reportportal.model.externalsystem.PostFormField;
import java.util.Map;
import java.util.Optional;

public class DelegatingColumnConverter implements IssueColumnConverter {

  private final Map<MondayColumnType, IssueColumnConverter> delegateMapping;

  private final IssueColumnConverter fallbackConverter;

  public DelegatingColumnConverter(Map<MondayColumnType, IssueColumnConverter> delegateMapping,
      IssueColumnConverter fallbackConverter) {
    this.delegateMapping = delegateMapping;
    this.fallbackConverter = fallbackConverter;
  }

  @Override
  public Optional<String> convert(PostFormField field) {
    return MondayColumnType.of(field.getFieldType())
        .flatMap(t -> ofNullable(delegateMapping.get(t))).map(c -> c.convert(field))
        .orElseGet(() -> fallbackConverter.convert(field));
  }
}
