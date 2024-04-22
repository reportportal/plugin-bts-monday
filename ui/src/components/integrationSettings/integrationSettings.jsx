import React from 'react';
import { useDispatch } from 'react-redux';
import { LABELS } from '../constans';

export const IntegrationSettings = (props) => {
  const { data, goToPreviousPage, onUpdate, isGlobal, ...extensionProps } = props;
  const {
    actions: { showModalAction, hideModalAction },
    components: {
      IntegrationSettings: IntegrationSettingsContainer,
      BtsAuthFieldsInfo,
      BtsPropertiesForIssueForm,
    },
    utils: { getDefectFormFields },
    constants: { BTS_FIELDS_FORM },
  } = extensionProps;

  const dispatch = useDispatch();

  const fieldsConfig = [
    {
      value: data.integrationParameters.url,
      message: LABELS.URL,
    },
    {
      value: data.integrationParameters.project,
      message: LABELS.BOARD_ID,
    },
  ];

  const getConfirmationFunc = (testConnection) => (integrationData, integrationMetaData) => {
    onUpdate(
      integrationData,
      () => {
        dispatch(hideModalAction());
        testConnection();
      },
      integrationMetaData,
    );
  };

  const editAuthorizationClickHandler = (testConnection) => {
    const {
      data: { name, integrationParameters, integrationType },
    } = props;

    dispatch(
      showModalAction({
        id: 'addIntegrationModal',
        data: {
          isGlobal,
          onConfirm: getConfirmationFunc(testConnection),
          instanceType: integrationType.name,
          customProps: {
            initialData: {
              ...integrationParameters,
              integrationName: name,
            },
            editAuthMode: true,
          },
        },
      }),
    );
  };

  const getEditAuthConfig = () => ({
    content: <BtsAuthFieldsInfo fieldsConfig={fieldsConfig} />,
    onClick: editAuthorizationClickHandler,
  });

  const onSubmit = (integrationData, callback, metaData) => {
    const { fields, checkedFieldsIds = {}, ...meta } = metaData;
    const defectFormFields = getDefectFormFields(fields, checkedFieldsIds, integrationData);

    onUpdate({ defectFormFields }, callback, meta);
  };

  return (
    <IntegrationSettingsContainer
      data={data}
      goToPreviousPage={goToPreviousPage}
      onUpdate={onSubmit}
      editAuthConfig={getEditAuthConfig()}
      isGlobal={isGlobal}
      formFieldsComponent={BtsPropertiesForIssueForm}
      formKey={BTS_FIELDS_FORM}
      isEmptyConfiguration={
        !data.integrationParameters.defectFormFields ||
        !data.integrationParameters.defectFormFields.length
      }
    />
  );
};
