package com.epam.reportportal.extension.monday.service.issue;

import static com.epam.reportportal.rules.commons.validation.Suppliers.formattedSupplier;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.entity.item.TestItem;
import org.apache.commons.lang3.StringUtils;

public class IssueDescriptionProvider {

  public static final String BACK_LINK_TITLE = "Back link to Report Portal";
  public static final String COMMENTS_TITLE = "Test Item comments:";

  public String provide(PostTicketRQ ticketRQ, TestItem item, String link) {
    StringBuilder descriptionBuilder = new StringBuilder();
    if (StringUtils.isNotBlank(link)) {
      descriptionBuilder.append(formattedSupplier(
          "<p><a href=\"{}\" target=\"_blank\" rel=\"noopener noreferrer\">{}</a></p>", link,
          BACK_LINK_TITLE
      ).get());
    }
    if (ticketRQ.getIsIncludeComments()) {
      if (StringUtils.isNotBlank(link)) {
        ofNullable(item.getItemResults()).flatMap(result -> ofNullable(result.getIssue()))
            .ifPresent(issue -> {
              if (StringUtils.isNotBlank(issue.getIssueDescription())) {
                descriptionBuilder.append(
                        formattedSupplier("<p><b>{}</b></p>", COMMENTS_TITLE).get())
                    .append(formattedSupplier("<p>{}</p>", issue.getIssueDescription()).get());
              }
            });
      }
    }
    return descriptionBuilder.toString();

  }
}
