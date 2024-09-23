package com.epam.reportportal.extension.monday.model.enums;

public enum MondayColumnId {

  NAME("name");

  private final String value;

  MondayColumnId(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public boolean matches(String value) {
    return this.value.equals(value);
  }
}
