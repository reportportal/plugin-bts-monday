package com.epam.reportportal.extension.monday.service.issue.converter;

import com.epam.reportportal.extension.monday.model.enums.MondayColumnId;
import com.epam.reportportal.extension.monday.service.column.converter.IssueColumnConverter;
import com.epam.reportportal.model.externalsystem.PostFormField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;

public class IssueParamsConverter {

  private final IssueColumnConverter issueColumnConverter;

  public IssueParamsConverter(IssueColumnConverter issueColumnConverter) {
    this.issueColumnConverter = issueColumnConverter;
  }

  public Map<String, String> convert(List<PostFormField> fields) {
    Map<String, String> issueParams = new HashMap<>();

    fields.stream().filter(f -> !MondayColumnId.NAME.matches(f.getId()))
        .filter(f -> CollectionUtils.isNotEmpty(f.getValue())).forEach(
            f -> issueColumnConverter.convert(f).ifPresent(v -> issueParams.put(f.getId(), v)));

    return issueParams;
  }
}
