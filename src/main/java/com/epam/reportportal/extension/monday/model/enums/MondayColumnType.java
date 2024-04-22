package com.epam.reportportal.extension.monday.model.enums;

import java.util.Arrays;
import java.util.Optional;

public enum MondayColumnType {

  LINK("link"), STATUS("status");

  private final String value;

  MondayColumnType(String value) {
    this.value = value;
  }

  public static Optional<MondayColumnType> of(String value) {
    return Arrays.stream(values()).filter(t -> t.value.equals(value)).findFirst();
  }

  public String getValue() {
    return value;
  }

  public boolean matches(String value) {
    return this.value.equals(value);
  }

}
