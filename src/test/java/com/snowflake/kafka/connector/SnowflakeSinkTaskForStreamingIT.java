package com.snowflake.kafka.connector;

import com.snowflake.kafka.connector.internal.LoggerHandler;
import com.snowflake.kafka.connector.internal.TestUtils;
import com.snowflake.kafka.connector.internal.streaming.InMemorySinkTaskContext;
import com.snowflake.kafka.connector.internal.streaming.IngestionMethodConfig;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.core.JsonProcessingException;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.JsonNode;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT;
import static com.snowflake.kafka.connector.SnowflakeSinkTask.TASK_INSTANCE_TAG_FORMAT;

/**
 * Sink Task IT test which uses {@link
 * com.snowflake.kafka.connector.internal.streaming.SnowflakeSinkServiceV2}
 */
public class SnowflakeSinkTaskForStreamingIT {

  private String topicName;
  private static int partition = 0;
  private TopicPartition topicPartition;

  @Mock
  Logger logger = Mockito.mock(Logger.class);

  @InjectMocks
  @Spy
  private LoggerHandler loggerHandler = Mockito.spy(new LoggerHandler(this.getClass().getName()));

  @InjectMocks private SnowflakeSinkTask task1 = new SnowflakeSinkTask();

  @Before
  public void setup() {
    topicName = TestUtils.randomTableName();
    topicPartition = new TopicPartition(topicName, partition);
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void after() {
    TestUtils.dropTable(topicName);
  }

  @Test
  public void testSinkTask() throws Exception {
    Map<String, String> config = TestUtils.getConfForStreaming();
    SnowflakeSinkConnectorConfig.setDefaultValues(config);
    config.put(BUFFER_COUNT_RECORDS, "1"); // override

    config.put(INGESTION_METHOD_OPT, IngestionMethodConfig.SNOWPIPE_STREAMING.toString());

    SnowflakeSinkTask sinkTask = new SnowflakeSinkTask();

    // Inits the sinktaskcontext
    sinkTask.initialize(new InMemorySinkTaskContext(Collections.singleton(topicPartition)));
    sinkTask.start(config);
    ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
    topicPartitions.add(new TopicPartition(topicName, partition));
    sinkTask.open(topicPartitions);

    // send regular data
    List<SinkRecord> records = TestUtils.createJsonStringSinkRecords(0, 1, topicName, partition);
    sinkTask.put(records);

    // commit offset
    final Map<TopicPartition, OffsetAndMetadata> offsetMap = new HashMap<>();
    offsetMap.put(topicPartitions.get(0), new OffsetAndMetadata(10000));

    TestUtils.assertWithRetry(() -> sinkTask.preCommit(offsetMap).size() == 1, 20, 5);

    TestUtils.assertWithRetry(
        () -> sinkTask.preCommit(offsetMap).get(topicPartitions.get(0)).offset() == 1, 20, 5);

    sinkTask.close(topicPartitions);
    sinkTask.stop();
  }

  @Test
  public void testSinkTaskWithMultipleOpenClose() throws Exception {
    int sinkTaskStartCount = 0;
    int sinkTaskCreationCount = 2;
    int sinkTaskId = 0;

    // set up logging
    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    Mockito.when(logger.isWarnEnabled()).thenReturn(true);

    Map<String, String> config = TestUtils.getConfForStreaming();
    SnowflakeSinkConnectorConfig.setDefaultValues(config);
    config.put(BUFFER_COUNT_RECORDS, "1"); // override
    config.put(INGESTION_METHOD_OPT, IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put()

    SnowflakeSinkTask sinkTask = new SnowflakeSinkTask();
    // Inits the sinktaskcontext
    sinkTask.initialize(new InMemorySinkTaskContext(Collections.singleton(topicPartition)));

    sinkTaskStartCount++;
    String expectedTask1Tag = getExpectedLogTag(taskId, taskCreationCount, task1StartCount);
    Mockito.doCallRealMethod().when(loggerHandler).setLoggerInstanceTag(expectedTask1Tag);
    sinkTask.start(config);
    Mockito.verify(loggerHandler, Mockito.times(1)).setLoggerInstanceTag(expectedTask1Tag);
    Mockito.verify(logger, Mockito.times(2))
      .info(
        AdditionalMatchers.and(Mockito.contains(expectedTask1Tag), Mockito.contains("start")));

    ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
    topicPartitions.add(new TopicPartition(topicName, partition));
    sinkTask.open(topicPartitions);

    final long noOfRecords = 1l;
    final long lastOffsetNo = noOfRecords - 1;

    // send regular data
    List<SinkRecord> records =
        TestUtils.createJsonStringSinkRecords(0, noOfRecords, topicName, partition);
    sinkTask.put(records);

    // commit offset
    final Map<TopicPartition, OffsetAndMetadata> offsetMap = new HashMap<>();
    offsetMap.put(topicPartitions.get(0), new OffsetAndMetadata(lastOffsetNo));

    TestUtils.assertWithRetry(() -> sinkTask.preCommit(offsetMap).size() == 1, 20, 5);

    // precommit is one more than offset last inserted
    TestUtils.assertWithRetry(
        () -> sinkTask.preCommit(offsetMap).get(topicPartitions.get(0)).offset() == noOfRecords,
        20,
        5);

    sinkTask.close(topicPartitions);

    // Add one more partition
    topicPartitions.add(new TopicPartition(topicName, partition + 1));

    sinkTask.open(topicPartitions);

    // trying to put same records
    sinkTask.put(records);

    List<SinkRecord> recordsWithAnotherPartition =
        TestUtils.createJsonStringSinkRecords(0, noOfRecords, topicName, partition + 1);
    sinkTask.put(recordsWithAnotherPartition);

    // Adding to offsetMap so that this gets into precommit
    offsetMap.put(topicPartitions.get(1), new OffsetAndMetadata(lastOffsetNo));

    TestUtils.assertWithRetry(() -> sinkTask.preCommit(offsetMap).size() == 2, 20, 5);

    TestUtils.assertWithRetry(
        () -> sinkTask.preCommit(offsetMap).get(topicPartitions.get(0)).offset() == 1, 20, 5);

    TestUtils.assertWithRetry(
        () -> sinkTask.preCommit(offsetMap).get(topicPartitions.get(1)).offset() == 1, 20, 5);

    sinkTask.close(topicPartitions);

    sinkTask.stop();

    ResultSet resultSet = TestUtils.showTable(topicName);
    LinkedList<String> contentResult = new LinkedList<>();
    LinkedList<String> metadataResult = new LinkedList<>();

    while (resultSet.next()) {
      contentResult.add(resultSet.getString("RECORD_CONTENT"));
      metadataResult.add(resultSet.getString("RECORD_METADATA"));
    }
    resultSet.close();
    assert metadataResult.size() == 2;
    assert contentResult.size() == 2;
    ObjectMapper mapper = new ObjectMapper();

    Set<Long> partitionsInTable = new HashSet<>();
    metadataResult.forEach(
        s -> {
          try {
            JsonNode metadata = mapper.readTree(s);
            metadata.get("offset").asText().equals("0");
            partitionsInTable.add(metadata.get("partition").asLong());
          } catch (JsonProcessingException e) {
            Assert.fail();
          }
        });

    assert partitionsInTable.size() == 2;
  }

  private String getExpectedLogTag(String taskId, int taskCreationCount, int taskStartCount) {
    return Utils.formatString(TASK_INSTANCE_TAG_FORMAT, taskId, taskCreationCount, taskStartCount);
  }
}
