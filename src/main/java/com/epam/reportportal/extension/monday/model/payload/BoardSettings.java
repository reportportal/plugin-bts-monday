package com.epam.reportportal.extension.monday.model.payload;

import java.io.Serializable;
import java.util.Map;

public class BoardSettings implements Serializable {

  private Map<String, String> labels;

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }
}
