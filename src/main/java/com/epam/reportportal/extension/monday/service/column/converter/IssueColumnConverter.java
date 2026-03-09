package com.epam.reportportal.extension.monday.service.column.converter;

import com.epam.reportportal.base.infrastructure.model.externalsystem.PostFormField;
import java.util.Optional;

public interface IssueColumnConverter {

  Optional<String> convert(PostFormField field);
}
