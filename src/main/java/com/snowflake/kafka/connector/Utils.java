/*
 * Copyright (c) 2019 Snowflake Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.snowflake.kafka.connector;

import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.BehaviorOnNullValues.VALIDATOR;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.DELIVERY_GUARANTEE;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.JMX_OPT;

import com.google.common.annotations.VisibleForTesting;
import com.snowflake.kafka.connector.internal.BufferThreshold;
import com.snowflake.kafka.connector.internal.Logging;
import com.snowflake.kafka.connector.internal.SnowflakeErrors;
import com.snowflake.kafka.connector.internal.SnowflakeKafkaConnectorException;
import com.snowflake.kafka.connector.internal.streaming.IngestionMethodConfig;
import com.snowflake.kafka.connector.internal.streaming.StreamingUtils;
import io.confluent.connect.avro.AvroConverterConfig;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.avro.Schema;
import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Various arbitrary helper functions */
public class Utils {

  // Connector version, change every release
  public static final String VERSION = "1.8.1";

  // connector parameter list
  public static final String NAME = "name";
  public static final String SF_DATABASE = "snowflake.database.name";
  public static final String SF_SCHEMA = "snowflake.schema.name";
  public static final String SF_USER = "snowflake.user.name";
  public static final String SF_PRIVATE_KEY = "snowflake.private.key";
  public static final String SF_URL = "snowflake.url.name";
  public static final String SF_SSL = "sfssl"; // for test only
  public static final String SF_WAREHOUSE = "sfwarehouse"; // for test only
  public static final String PRIVATE_KEY_PASSPHRASE = "snowflake.private.key" + ".passphrase";

  /**
   * This value should be present if ingestion method is {@link
   * IngestionMethodConfig#SNOWPIPE_STREAMING}
   */
  public static final String SF_ROLE = "snowflake.role.name";

  // constants strings
  private static final String KAFKA_OBJECT_PREFIX = "SNOWFLAKE_KAFKA_CONNECTOR";

  // task id
  public static final String TASK_ID = "task_id";

  // jvm proxy
  public static final String HTTP_USE_PROXY = "http.useProxy";
  public static final String HTTPS_PROXY_HOST = "https.proxyHost";
  public static final String HTTPS_PROXY_PORT = "https.proxyPort";
  public static final String HTTP_PROXY_HOST = "http.proxyHost";
  public static final String HTTP_PROXY_PORT = "http.proxyPort";

  public static final String JDK_HTTP_AUTH_TUNNELING = "jdk.http.auth.tunneling.disabledSchemes";
  public static final String HTTPS_PROXY_USER = "https.proxyUser";
  public static final String HTTPS_PROXY_PASSWORD = "https.proxyPassword";
  public static final String HTTP_PROXY_USER = "http.proxyUser";
  public static final String HTTP_PROXY_PASSWORD = "http.proxyPassword";

  // jdbc log dir
  public static final String JAVA_IO_TMPDIR = "java.io.tmpdir";

  private static final Random random = new Random();

  // mvn repo
  private static final String MVN_REPO =
      "https://repo1.maven.org/maven2/com/snowflake/snowflake-kafka-connector/";

  public static final String TABLE_COLUMN_CONTENT = "RECORD_CONTENT";
  public static final String TABLE_COLUMN_METADATA = "RECORD_METADATA";

  private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class.getName());

  /**
   * check the connector version from Maven repo, report if any update version is available.
   *
   * <p>A URl connection timeout is added in case Maven repo is not reachable in a proxy'd
   * environment. Returning false from this method doesnt have any side effects to start the
   * connector.
   */
  static boolean checkConnectorVersion() {
    LOGGER.info(Logging.logMessage("Current Snowflake Kafka Connector Version: {}", VERSION));
    try {
      String latestVersion = null;
      int largestNumber = 0;
      URLConnection urlConnection = new URL(MVN_REPO).openConnection();
      urlConnection.setConnectTimeout(5000);
      urlConnection.setReadTimeout(5000);
      InputStream input = urlConnection.getInputStream();
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));
      String line;
      Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+?)");
      while ((line = bufferedReader.readLine()) != null) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
          String version = matcher.group(1);
          String[] numbers = version.split("\\.");
          int num =
              Integer.parseInt(numbers[0]) * 10000
                  + Integer.parseInt(numbers[1]) * 100
                  + Integer.parseInt(numbers[2]);
          if (num > largestNumber) {
            largestNumber = num;
            latestVersion = version;
          }
        }
      }

      if (latestVersion == null) {
        throw new Exception("can't retrieve version number from Maven repo");
      } else if (!latestVersion.equals(VERSION)) {
        LOGGER.warn(
            Logging.logMessage(
                "Connector update is available, please"
                    + " upgrade Snowflake Kafka Connector ({} -> {}) ",
                VERSION,
                latestVersion));
      }
    } catch (Exception e) {
      LOGGER.warn(
          Logging.logMessage(
              "can't verify latest connector version " + "from Maven Repo\n{}", e.getMessage()));
      return false;
    }

    return true;
  }

  /**
   * @param appName connector name
   * @return connector object prefix
   */
  private static String getObjectPrefix(String appName) {
    return KAFKA_OBJECT_PREFIX + "_" + appName;
  }

  /**
   * generate stage name by given table
   *
   * @param appName connector name
   * @param table table name
   * @return stage name
   */
  public static String stageName(String appName, String table) {
    String stageName = getObjectPrefix(appName) + "_STAGE_" + table;

    LOGGER.debug(Logging.logMessage("generated stage name: {}", stageName));

    return stageName;
  }

  /**
   * generate pipe name by given table and partition
   *
   * @param appName connector name
   * @param table table name
   * @param partition partition name
   * @return pipe name
   */
  public static String pipeName(String appName, String table, int partition) {
    String pipeName = getObjectPrefix(appName) + "_PIPE_" + table + "_" + partition;

    LOGGER.debug(Logging.logMessage("generated pipe name: {}", pipeName));

    return pipeName;
  }

  /**
   * Read JDBC logging directory from environment variable JDBC_LOG_DIR and set that in System
   * property
   */
  public static void setJDBCLoggingDirectory() {
    String jdbcTmpDir = System.getenv(SnowflakeSinkConnectorConfig.SNOWFLAKE_JDBC_LOG_DIR);
    if (jdbcTmpDir != null) {
      File jdbcTmpDirObj = new File(jdbcTmpDir);
      if (jdbcTmpDirObj.isDirectory()) {
        LOGGER.info(Logging.logMessage("jdbc tracing directory = {}", jdbcTmpDir));
        System.setProperty(JAVA_IO_TMPDIR, jdbcTmpDir);
      } else {
        LOGGER.info(
            Logging.logMessage(
                "invalid JDBC_LOG_DIR {} defaulting to {}",
                jdbcTmpDir,
                System.getProperty(JAVA_IO_TMPDIR)));
      }
    }
  }

  /**
   * validate whether proxy settings in the config is valid
   *
   * @param config connector configuration
   */
  static void validateProxySetting(Map<String, String> config) {
    String host =
        SnowflakeSinkConnectorConfig.getProperty(
            config, SnowflakeSinkConnectorConfig.JVM_PROXY_HOST);
    String port =
        SnowflakeSinkConnectorConfig.getProperty(
            config, SnowflakeSinkConnectorConfig.JVM_PROXY_PORT);
    // either both host and port are provided or none of them are provided
    if (host != null ^ port != null) {
      throw SnowflakeErrors.ERROR_0022.getException(
          SnowflakeSinkConnectorConfig.JVM_PROXY_HOST
              + " and "
              + SnowflakeSinkConnectorConfig.JVM_PROXY_PORT
              + " must be provided together");
    } else if (host != null) {
      String username =
          SnowflakeSinkConnectorConfig.getProperty(
              config, SnowflakeSinkConnectorConfig.JVM_PROXY_USERNAME);
      String password =
          SnowflakeSinkConnectorConfig.getProperty(
              config, SnowflakeSinkConnectorConfig.JVM_PROXY_PASSWORD);
      // either both username and password are provided or none of them are provided
      if (username != null ^ password != null) {
        throw SnowflakeErrors.ERROR_0023.getException(
            SnowflakeSinkConnectorConfig.JVM_PROXY_USERNAME
                + " and "
                + SnowflakeSinkConnectorConfig.JVM_PROXY_PASSWORD
                + " must be provided together");
      }
    }
  }

  /**
   * Enable JVM proxy
   *
   * @param config connector configuration
   * @return false if wrong config
   */
  static boolean enableJVMProxy(Map<String, String> config) {
    String host =
        SnowflakeSinkConnectorConfig.getProperty(
            config, SnowflakeSinkConnectorConfig.JVM_PROXY_HOST);
    String port =
        SnowflakeSinkConnectorConfig.getProperty(
            config, SnowflakeSinkConnectorConfig.JVM_PROXY_PORT);
    if (host != null && port != null) {
      LOGGER.info(Logging.logMessage("enable jvm proxy: {}:{}", host, port));

      // enable https proxy
      System.setProperty(HTTP_USE_PROXY, "true");
      System.setProperty(HTTP_PROXY_HOST, host);
      System.setProperty(HTTP_PROXY_PORT, port);
      System.setProperty(HTTPS_PROXY_HOST, host);
      System.setProperty(HTTPS_PROXY_PORT, port);

      // set username and password
      String username =
          SnowflakeSinkConnectorConfig.getProperty(
              config, SnowflakeSinkConnectorConfig.JVM_PROXY_USERNAME);
      String password =
          SnowflakeSinkConnectorConfig.getProperty(
              config, SnowflakeSinkConnectorConfig.JVM_PROXY_PASSWORD);
      if (username != null && password != null) {
        Authenticator.setDefault(
            new Authenticator() {
              @Override
              public PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password.toCharArray());
              }
            });
        System.setProperty(JDK_HTTP_AUTH_TUNNELING, "");
        System.setProperty(HTTP_PROXY_USER, username);
        System.setProperty(HTTP_PROXY_PASSWORD, password);
        System.setProperty(HTTPS_PROXY_USER, username);
        System.setProperty(HTTPS_PROXY_PASSWORD, password);
      }
    }

    return true;
  }

  /**
   * validates that given name is a valid snowflake object identifier
   *
   * @param objName snowflake object name
   * @return true if given object name is valid
   */
  static boolean isValidSnowflakeObjectIdentifier(String objName) {
    return objName.matches("^[_a-zA-Z]{1}[_$a-zA-Z0-9]+$");
  }

  /**
   * validates that given name is a valid snowflake application name, support '-'
   *
   * @param appName snowflake application name
   * @return true if given application name is valid
   */
  static boolean isValidSnowflakeApplicationName(String appName) {
    return appName.matches("^[-_a-zA-Z]{1}[-_$a-zA-Z0-9]+$");
  }

  static boolean isValidSnowflakeTableName(String tableName) {
    return tableName.matches("^([_a-zA-Z]{1}[_$a-zA-Z0-9]+\\.){0,2}[_a-zA-Z]{1}[_$a-zA-Z0-9]+$");
  }

  /**
   * Validate input configuration
   *
   * @param config configuration Map
   * @return connector name
   */
  static String validateConfig(Map<String, String> config) {
    boolean configIsValid = true; // verify all config

    // define the input parameters / keys in one place as static constants,
    // instead of using them directly
    // define the thresholds statically in one place as static constants,
    // instead of using the values directly

    // unique name of this connector instance
    String connectorName = config.getOrDefault(SnowflakeSinkConnectorConfig.NAME, "");
    if (connectorName.isEmpty() || !isValidSnowflakeApplicationName(connectorName)) {
      LOGGER.error(
          Logging.logMessage(
              "{} is empty or invalid. It "
                  + "should match Snowflake object identifier syntax. Please see the "
                  + "documentation.",
              SnowflakeSinkConnectorConfig.NAME));
      configIsValid = false;
    }

    // If config doesnt have ingestion method defined, default is snowpipe or if snowpipe is
    // explicitly passed in as ingestion method
    // Below checks are just for snowpipe.
    if (!config.containsKey(INGESTION_METHOD_OPT)
        || config
            .get(INGESTION_METHOD_OPT)
            .equalsIgnoreCase(IngestionMethodConfig.SNOWPIPE.toString())) {
      if (!BufferThreshold.validateBufferThreshold(config, IngestionMethodConfig.SNOWPIPE)) {
        configIsValid = false;
      }

      if (config.containsKey(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG)
          && Boolean.parseBoolean(
              config.get(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG))) {
        configIsValid = false;
        LOGGER.error(
            Logging.logMessage(
                "Schematization is only available with {}.",
                IngestionMethodConfig.SNOWPIPE_STREAMING.toString()));
      }
    }

    if (config.containsKey(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP)
        && parseTopicToTableMap(config.get(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP))
            == null) {
      configIsValid = false;
    }

    // sanity check
    if (!config.containsKey(SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE)) {
      LOGGER.error(
          Logging.logMessage(
              "{} cannot be empty.", SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE));
      configIsValid = false;
    }

    // sanity check
    if (!config.containsKey(SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA)) {
      LOGGER.error(
          Logging.logMessage("{} cannot be empty.", SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA));
      configIsValid = false;
    }

    if (!config.containsKey(SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY)) {
      LOGGER.error(
          Logging.logMessage(
              "{} cannot be empty.", SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY));
      configIsValid = false;
    }

    if (!config.containsKey(SnowflakeSinkConnectorConfig.SNOWFLAKE_USER)) {
      LOGGER.error(
          Logging.logMessage("{} cannot be empty.", SnowflakeSinkConnectorConfig.SNOWFLAKE_USER));
      configIsValid = false;
    }

    if (!config.containsKey(SnowflakeSinkConnectorConfig.SNOWFLAKE_URL)) {
      LOGGER.error(
          Logging.logMessage("{} cannot be empty.", SnowflakeSinkConnectorConfig.SNOWFLAKE_URL));
      configIsValid = false;
    }
    // jvm proxy settings
    try {
      validateProxySetting(config);
    } catch (SnowflakeKafkaConnectorException e) {
      LOGGER.error(Logging.logMessage("Proxy settings error: ", e.getMessage()));
      configIsValid = false;
    }

    // set jdbc logging directory
    Utils.setJDBCLoggingDirectory();

    // validate whether kafka provider config is a valid value
    if (config.containsKey(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG)) {
      try {
        SnowflakeSinkConnectorConfig.KafkaProvider.of(
            config.get(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG));
      } catch (IllegalArgumentException exception) {
        LOGGER.error(Logging.logMessage("Kafka provider config error:{}", exception.getMessage()));
        configIsValid = false;
      }
    }

    if (config.containsKey(BEHAVIOR_ON_NULL_VALUES_CONFIG)) {
      try {
        // This throws an exception if config value is invalid.
        VALIDATOR.ensureValid(
            BEHAVIOR_ON_NULL_VALUES_CONFIG, config.get(BEHAVIOR_ON_NULL_VALUES_CONFIG));
      } catch (ConfigException exception) {
        LOGGER.error(
            Logging.logMessage(
                "Kafka config:{} error:{}",
                BEHAVIOR_ON_NULL_VALUES_CONFIG,
                exception.getMessage()));
        configIsValid = false;
      }
    }

    if (config.containsKey(JMX_OPT)) {
      if (!(config.get(JMX_OPT).equalsIgnoreCase("true")
          || config.get(JMX_OPT).equalsIgnoreCase("false"))) {
        LOGGER.error(Logging.logMessage("Kafka config:{} should either be true or false", JMX_OPT));
        configIsValid = false;
      }
    }

    try {
      SnowflakeSinkConnectorConfig.IngestionDeliveryGuarantee.of(
          config.getOrDefault(
              DELIVERY_GUARANTEE,
              SnowflakeSinkConnectorConfig.IngestionDeliveryGuarantee.AT_LEAST_ONCE.name()));
    } catch (IllegalArgumentException exception) {
      LOGGER.error(
          Logging.logMessage(
              "Delivery Guarantee config:{} error:{}", DELIVERY_GUARANTEE, exception.getMessage()));
      configIsValid = false;
    }

    // Check all config values for ingestion method == IngestionMethodConfig.SNOWPIPE_STREAMING
    final boolean isStreamingConfigValid = StreamingUtils.isStreamingSnowpipeConfigValid(config);

    if (!configIsValid || !isStreamingConfigValid) {
      throw SnowflakeErrors.ERROR_0001.getException();
    }

    return connectorName;
  }

  /**
   * modify invalid application name in config and return the generated application name
   *
   * @param config input config object
   */
  public static void convertAppName(Map<String, String> config) {
    String appName = config.getOrDefault(SnowflakeSinkConnectorConfig.NAME, "");
    // If appName is empty the following call will throw error
    String validAppName = generateValidName(appName, new HashMap<String, String>());

    config.put(SnowflakeSinkConnectorConfig.NAME, validAppName);
  }

  /**
   * verify topic name, and generate valid table name
   *
   * @param topic input topic name
   * @param topic2table topic to table map
   * @return valid table name
   */
  public static String tableName(String topic, Map<String, String> topic2table) {
    return generateValidName(topic, topic2table);
  }

  /**
   * verify topic name, and generate valid table/application name
   *
   * @param topic input topic name
   * @param topic2table topic to table map
   * @return valid table/application name
   */
  public static String generateValidName(String topic, Map<String, String> topic2table) {
    final String PLACE_HOLDER = "_";
    if (topic == null || topic.isEmpty()) {
      throw SnowflakeErrors.ERROR_0020.getException("topic name: " + topic);
    }
    if (topic2table.containsKey(topic)) {
      return topic2table.get(topic);
    }
    if (Utils.isValidSnowflakeObjectIdentifier(topic)) {
      return topic;
    }
    int hash = Math.abs(topic.hashCode());

    StringBuilder result = new StringBuilder();

    int index = 0;
    // first char
    if (topic.substring(index, index + 1).matches("[_a-zA-Z]")) {
      result.append(topic.charAt(0));
      index++;
    } else {
      result.append(PLACE_HOLDER);
    }
    while (index < topic.length()) {
      if (topic.substring(index, index + 1).matches("[_$a-zA-Z0-9]")) {
        result.append(topic.charAt(index));
      } else {
        result.append(PLACE_HOLDER);
      }
      index++;
    }

    result.append(PLACE_HOLDER);
    result.append(hash);

    return result.toString();
  }

  public static Map<String, String> parseTopicToTableMap(String input) {
    Map<String, String> topic2Table = new HashMap<>();
    boolean isInvalid = false;
    for (String str : input.split(",")) {
      String[] tt = str.split(":");

      if (tt.length != 2 || tt[0].trim().isEmpty() || tt[1].trim().isEmpty()) {
        LOGGER.error(
            Logging.logMessage(
                "Invalid {} config format: {}",
                SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP,
                input));
        return null;
      }

      String topic = tt[0].trim();
      String table = tt[1].trim();

      if (!isValidSnowflakeTableName(table)) {
        LOGGER.error(
            Logging.logMessage(
                "table name {} should have at least 2 "
                    + "characters, start with _a-zA-Z, and only contains "
                    + "_$a-zA-z0-9",
                table));
        isInvalid = true;
      }

      if (topic2Table.containsKey(topic)) {
        LOGGER.error(Logging.logMessage("topic name {} is duplicated", topic));
        isInvalid = true;
      }

      topic2Table.put(tt[0].trim(), tt[1].trim());
    }
    if (isInvalid) {
      throw SnowflakeErrors.ERROR_0021.getException();
    }
    return topic2Table;
  }

  static final String loginPropList[] = {SF_URL, SF_USER, SF_SCHEMA, SF_DATABASE};

  public static boolean isSingleFieldValid(Config result) {
    // if any single field validation failed
    for (ConfigValue v : result.configValues()) {
      if (!v.errorMessages().isEmpty()) {
        return false;
      }
    }
    // if any of url, user, schema, database or password is empty
    // update error message and return false
    boolean isValidate = true;
    final String errorMsg = " must be provided";
    Map<String, ConfigValue> validateMap = validateConfigToMap(result);
    //
    for (String prop : loginPropList) {
      if (validateMap.get(prop).value() == null) {
        updateConfigErrorMessage(result, prop, errorMsg);
        isValidate = false;
      }
    }

    return isValidate;
  }

  public static Map<String, ConfigValue> validateConfigToMap(final Config result) {
    Map<String, ConfigValue> validateMap = new HashMap<>();
    for (ConfigValue v : result.configValues()) {
      validateMap.put(v.name(), v);
    }
    return validateMap;
  }

  public static void updateConfigErrorMessage(Config result, String key, String msg) {
    for (ConfigValue v : result.configValues()) {
      if (v.name().equals(key)) {
        v.addErrorMessage(key + msg);
      }
    }
  }

  /**
   * get schema with its subject being [topicName]-value
   *
   * @param topicName
   * @param schemaRegistryURL
   * @return the mapping from columnName to their data type
   */
  public static Map<String, String> getValueSchemaFromSchemaRegistryURL(
      final String topicName, final String schemaRegistryURL) {
    return getSchemaFromSchemaRegistryClient(
        topicName, getSchemaRegistryClientFromURL(schemaRegistryURL), "value");
  }

  private static SchemaRegistryClient getSchemaRegistryClientFromURL(
      final String schemaRegistryURL) {
    Map<String, String> srConfig = new HashMap<>();
    srConfig.put("schema.registry.url", schemaRegistryURL);
    AvroConverterConfig avroConverterConfig = new AvroConverterConfig(srConfig);
    return new CachedSchemaRegistryClient(
        avroConverterConfig.getSchemaRegistryUrls(),
        avroConverterConfig.getMaxSchemasPerSubject(),
        Collections.singletonList(new AvroSchemaProvider()),
        srConfig,
        avroConverterConfig.requestHeaders());
  }

  /**
   * get schema with its subject being [topicName]-[type]
   *
   * @param topicName
   * @param schemaRegistry the schema registry client
   * @param type can only be "value" or "key", indicating we get the value schema or the key schema
   * @return the mapping from columnName to their data type
   */
  @VisibleForTesting
  public static Map<String, String> getSchemaFromSchemaRegistryClient(
      final String topicName, final SchemaRegistryClient schemaRegistry, final String type) {
    String subjectName = topicName + "-" + type;
    SchemaMetadata schemaMetadata;
    try {
      schemaMetadata = schemaRegistry.getLatestSchemaMetadata(subjectName);
    } catch (Exception e) {
      throw SnowflakeErrors.ERROR_0012.getException();
    }
    Map<String, String> schemaMap = new HashMap<>();
    if (schemaMetadata != null) {
      AvroSchema schema = new AvroSchema(schemaMetadata.getSchema());
      for (Schema.Field field : schema.rawSchema().getFields()) {
        Schema fieldSchema = field.schema();
        if (!schemaMap.containsKey(field.name())) {
          switch (fieldSchema.getType()) {
            case BOOLEAN:
              schemaMap.put(field.name(), "boolean");
              break;
            case BYTES:
              schemaMap.put(field.name(), "binary");
              break;
            case DOUBLE:
              schemaMap.put(field.name(), "double");
              break;
            case FLOAT:
              schemaMap.put(field.name(), "float");
              break;
            case INT:
              schemaMap.put(field.name(), "int");
              break;
            case LONG:
              schemaMap.put(field.name(), "number");
              break;
            case STRING:
              schemaMap.put(field.name(), "string");
              break;
            case ARRAY:
              schemaMap.put(field.name(), "array");
              break;
            default:
              schemaMap.put(field.name(), "variant");
          }
        }
      }
    }
    return schemaMap;
  }

  /**
   * From the connector config extract whether the avro value converter is used
   *
   * @param connectorConfig
   * @return whether the avro value converter is used
   */
  public static boolean usesAvroValueConverter(final Map<String, String> connectorConfig) {
    List<String> validAvroConverter = new ArrayList<>();
    validAvroConverter.add("io.confluent.connect.avro.AvroConverter");
    if (connectorConfig.containsKey(SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD)) {
      String valueConverter =
          connectorConfig.get(SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD);
      return validAvroConverter.contains(valueConverter);
    } else {
      return false;
    }
  }

  /**
   * From get the schemaMap for the table from topics. Topics will be collected from topicToTableMap
   * or connectorConfig
   *
   * @param topicToTableMap
   * @param connectorConfig
   * @return the map from the columnName to their type
   */
  public static Map<String, String> getSchemaMap(
      final Map<String, String> topicToTableMap, final Map<String, String> connectorConfig) {
    Map<String, String> schemaMap = new HashMap<>();
    if (!topicToTableMap.isEmpty()) {
      for (String topic : topicToTableMap.keySet()) {
        Map<String, String> tempMap =
            Utils.getValueSchemaFromSchemaRegistryURL(
                topic, connectorConfig.get("value.converter.schema.registry.url"));
        schemaMap.putAll(tempMap);
      }
    } else {
      // if topic is not present in topic2table map, the table name must be the same with the
      // topic
      schemaMap =
          Utils.getValueSchemaFromSchemaRegistryURL(
              connectorConfig.get("topics"),
              connectorConfig.get("value.converter.schema.registry.url"));
    }
    return schemaMap;
  }
}
