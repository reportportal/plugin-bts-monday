package com.epam.reportportal.extension.monday.utils;

public class ParamUtils {


  public static String normalizeUrl(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }

}
