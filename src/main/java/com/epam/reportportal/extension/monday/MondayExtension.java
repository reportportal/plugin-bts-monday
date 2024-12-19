/*
 * Copyright 2021 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.monday;

import com.apollographql.apollo3.ApolloClient;
import com.apollographql.apollo3.network.http.DefaultHttpEngine;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.NamedPluginCommand;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.ReportPortalExtensionPoint;
import com.epam.reportportal.extension.common.IntegrationTypeProperties;
import com.epam.reportportal.extension.event.PluginEvent;
import com.epam.reportportal.extension.monday.client.GraphQLExecutor;
import com.epam.reportportal.extension.monday.client.MondayClientProvider;
import com.epam.reportportal.extension.monday.command.GetIssueCommand;
import com.epam.reportportal.extension.monday.command.GetIssueFieldsCommand;
import com.epam.reportportal.extension.monday.command.GetIssueTypesCommand;
import com.epam.reportportal.extension.monday.command.PostTicketCommand;
import com.epam.reportportal.extension.monday.command.RetrieveCreationParamsCommand;
import com.epam.reportportal.extension.monday.command.RetrieveUpdateParamsCommand;
import com.epam.reportportal.extension.monday.command.connection.TestConnectionCommand;
import com.epam.reportportal.extension.monday.event.plugin.PluginEventHandlerFactory;
import com.epam.reportportal.extension.monday.event.plugin.PluginEventListener;
import com.epam.reportportal.extension.monday.info.impl.PluginInfoProviderImpl;
import com.epam.reportportal.extension.monday.model.enums.MondayColumnType;
import com.epam.reportportal.extension.monday.service.column.converter.DefaultColumnConverter;
import com.epam.reportportal.extension.monday.service.column.converter.DelegatingColumnConverter;
import com.epam.reportportal.extension.monday.service.column.converter.IssueColumnConverter;
import com.epam.reportportal.extension.monday.service.column.converter.LinkColumnConverter;
import com.epam.reportportal.extension.monday.service.column.converter.StatusColumnConverter;
import com.epam.reportportal.extension.monday.service.issue.IssueDescriptionProvider;
import com.epam.reportportal.extension.monday.service.issue.converter.IssueParamsConverter;
import com.epam.reportportal.extension.monday.service.issue.log.sender.LogSenderProvider;
import com.epam.reportportal.extension.monday.utils.MemoizingSupplier;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.epam.ta.reportportal.binary.DataStoreService;
import com.epam.ta.reportportal.dao.IntegrationRepository;
import com.epam.ta.reportportal.dao.IntegrationTypeRepository;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.dao.TicketRepository;
import com.epam.ta.reportportal.dao.organization.OrganizationRepositoryCustom;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.jasypt.util.text.BasicTextEncryptor;
import org.pf4j.Extension;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

@Extension
public class MondayExtension implements ReportPortalExtensionPoint, DisposableBean {

  public static final String BINARY_DATA_PROPERTIES_FILE_ID = "binary-data.properties";
  private static final String MONDAY_GRAPHQL_URL = "https://api.monday.com/v2";
  private static final String MONDAY_UPLOAD_FILE_URL = "https://api.monday.com/v2/file";
  private static final String DOCUMENTATION_LINK_FIELD = "documentationLink";
  private static final String DOCUMENTATION_LINK = "https://reportportal.io/docs/plugins/Monday";
  private static final String PLUGIN_ID = "Monday";

  private final String resourcesDir;
  private final ObjectMapper objectMapper;
  private final RequestEntityConverter requestEntityConverter;
  private final IssueParamsConverter issueParamsConverter;
  private final IssueDescriptionProvider issueDescriptionProvider;
  private final Supplier<OkHttpClient> okHttpClientSupplier;
  private final Supplier<GraphQLExecutor> graphQLExecutor;
  private final Supplier<MondayClientProvider> mondayClientProvider;
  private final Supplier<LogSenderProvider> logSenderProviderSupplier;
  private final Supplier<ApplicationListener<PluginEvent>> pluginLoadedListenerSupplier;
  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private IntegrationTypeRepository integrationTypeRepository;
  @Autowired
  private IntegrationRepository integrationRepository;
  @Autowired
  private TicketRepository ticketRepository;
  @Autowired
  private ProjectRepository projectRepository;
  @Autowired
  private OrganizationRepositoryCustom organizationRepository;
  @Autowired
  private LogRepository logRepository;
  @Autowired
  private TestItemRepository testItemRepository;
  private final Supplier<Map<String, PluginCommand<?>>> pluginCommandMapping =
      new MemoizingSupplier<>(this::getCommands);
  @Autowired
  private BasicTextEncryptor textEncryptor;
  private final Supplier<Map<String, CommonPluginCommand<?>>> commonPluginCommandMapping =
      new MemoizingSupplier<>(this::getCommonCommands);
  @Autowired
  @Qualifier("attachmentDataStoreService")
  private DataStoreService dataStoreService;

  public MondayExtension(Map<String, Object> initParams) {
    resourcesDir =
        IntegrationTypeProperties.RESOURCES_DIRECTORY.getValue(initParams).map(String::valueOf)
            .orElse("");
    objectMapper = configureObjectMapper();

    pluginLoadedListenerSupplier = new MemoizingSupplier<>(() -> new PluginEventListener(PLUGIN_ID,
        new PluginEventHandlerFactory(integrationTypeRepository, integrationRepository,
            new PluginInfoProviderImpl(resourcesDir, BINARY_DATA_PROPERTIES_FILE_ID)
        )
    ));

    requestEntityConverter = new RequestEntityConverter(objectMapper);

    issueParamsConverter = getIssueParamsConverter();
    issueDescriptionProvider = new IssueDescriptionProvider();

    okHttpClientSupplier = new MemoizingSupplier<>(this::configureOkHttpClient);
    graphQLExecutor = new MemoizingSupplier<>(
        () -> new GraphQLExecutor(configureApolloClient(okHttpClientSupplier.get())));
    mondayClientProvider = new MemoizingSupplier<>(
        () -> new MondayClientProvider(MONDAY_UPLOAD_FILE_URL, okHttpClientSupplier.get(),
            textEncryptor, graphQLExecutor.get()
        ));
    logSenderProviderSupplier =
        new MemoizingSupplier<>(() -> new LogSenderProvider(dataStoreService));
  }

  protected ObjectMapper configureObjectMapper() {
    ObjectMapper om = new ObjectMapper();
    om.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
    om.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    om.registerModule(new JavaTimeModule());
    return om;
  }

  private OkHttpClient configureOkHttpClient() {
    return new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).connectTimeout(5, TimeUnit.SECONDS)
        .connectionPool(new ConnectionPool(2, 5, TimeUnit.SECONDS)).build();
  }

  private ApolloClient configureApolloClient(OkHttpClient okHttpClient) {
    return new ApolloClient.Builder().httpEngine(new DefaultHttpEngine(okHttpClient))
        .serverUrl(MONDAY_GRAPHQL_URL).build();
  }

  private IssueParamsConverter getIssueParamsConverter() {
    return new IssueParamsConverter(getIssueColumnConverter());
  }

  private IssueColumnConverter getIssueColumnConverter() {
    return new DelegatingColumnConverter(
        getIssueColumnConverterMapping(), new DefaultColumnConverter());
  }

  private Map<MondayColumnType, IssueColumnConverter> getIssueColumnConverterMapping() {
    return Map.of(
        MondayColumnType.STATUS, new StatusColumnConverter(), MondayColumnType.LINK,
        new LinkColumnConverter()
    );
  }

  @Override
  public Map<String, ?> getPluginParams() {
    Map<String, Object> params = new HashMap<>();
    params.put(ALLOWED_COMMANDS, new ArrayList<>(pluginCommandMapping.get().keySet()));
    params.put(DOCUMENTATION_LINK_FIELD, DOCUMENTATION_LINK);
    params.put(COMMON_COMMANDS, new ArrayList<>(commonPluginCommandMapping.get().keySet()));
    return params;
  }

  @Override
  public PluginCommand<?> getIntegrationCommand(String commandName) {
    return pluginCommandMapping.get().get(commandName);
  }

  @Override
  public CommonPluginCommand<?> getCommonCommand(String commandName) {
    return commonPluginCommandMapping.get().get(commandName);
  }

  @Override
  public IntegrationGroupEnum getIntegrationGroup() {
    return IntegrationGroupEnum.BTS;
  }

  @PostConstruct
  public void createIntegration() {
    initListeners();
  }

  private void initListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.addApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public void destroy() {
    removeListeners();
    graphQLExecutor.get().close();
  }

  private void removeListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.removeApplicationListener(pluginLoadedListenerSupplier.get());
  }

  private Map<String, CommonPluginCommand<?>> getCommonCommands() {
    List<CommonPluginCommand<?>> commands = new ArrayList<>();
    commands.add(new RetrieveCreationParamsCommand(textEncryptor));
    commands.add(new RetrieveUpdateParamsCommand(textEncryptor));
    commands.add(
        new GetIssueCommand(mondayClientProvider.get(), ticketRepository, integrationRepository));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));
  }

  private Map<String, PluginCommand<?>> getCommands() {
    List<PluginCommand<?>> commands = new ArrayList<>();
    commands.add(new TestConnectionCommand(mondayClientProvider.get()));
    commands.add(new GetIssueTypesCommand(projectRepository, organizationRepository));
    commands.add(
        new GetIssueFieldsCommand(projectRepository, mondayClientProvider.get(), objectMapper,
            organizationRepository));
    commands.add(
        new PostTicketCommand(projectRepository, requestEntityConverter, mondayClientProvider.get(),
            issueParamsConverter, issueDescriptionProvider, logSenderProviderSupplier.get(),
            objectMapper, testItemRepository, logRepository, organizationRepository
        ));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));

  }
}
