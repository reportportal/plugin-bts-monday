import React, { useEffect } from 'react';
import { LABELS } from '../constans';

export const IntegrationFormFields = (props) => {
  const { initialize, disabled, initialData, updateMetaData, ...extensionProps } = props;
  const {
    components: { FieldErrorHint, FieldElement, FieldText, FieldTextFlex },
    validators: { requiredField, btsUrl, btsBoardId, btsIntegrationName },
    constants: { SECRET_FIELDS_KEY },
  } = extensionProps;

  useEffect(() => {
    initialize(initialData);
    updateMetaData({
      [SECRET_FIELDS_KEY]: ['apiToken'],
    });
  }, []);

  return (
    <>
      <FieldElement
        name="integrationName"
        label={LABELS.INTEGRATION_NAME}
        validate={btsIntegrationName}
        disabled={disabled}
        isRequired
        dataAutomationId="integrationNameField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="url"
        label={LABELS.URL}
        validate={btsUrl}
        disabled={disabled}
        isRequired
        dataAutomationId="linkToBTSField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="project"
        label={LABELS.BOARD_ID}
        validate={btsBoardId}
        disabled={disabled}
        isRequired
        dataAutomationId="boardInBTSField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="apiToken"
        label={LABELS.TOKEN}
        disabled={disabled}
        validate={requiredField}
        isRequired
        dataAutomationId="apiTokenField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldTextFlex />
        </FieldErrorHint>
      </FieldElement>
    </>
  );
};
