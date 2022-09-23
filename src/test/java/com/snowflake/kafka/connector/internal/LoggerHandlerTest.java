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
package com.snowflake.kafka.connector.internal;

import com.snowflake.kafka.connector.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.util.UUID;

public class LoggerHandlerTest {
  // test constants
  private final String name = "test.logger.name";
  private final String msg = "super useful logging message";
  private final String expectedMsg = msg;
  private final String formatMsg = "a formatted msg is totally more useful than a unformatted '{}'";
  private final String expectedFormattedMsg =
      "a formatted msg is totally more useful than a unformatted 'super "
          + "useful logging message'";
  private final UUID kcGlobalInstanceId = UUID.randomUUID();
  private final UUID loggerInstanceId = UUID.randomUUID();
  private final String loggerInstanceIdDescriptor = "TEST";

  // mock and test setup, inject logger into loggerHandler
  @Mock(name = "logger")
  private Logger logger = Mockito.mock(Logger.class);

  @InjectMocks private LoggerHandler loggerHandler = new LoggerHandler(this.name);

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void close() {
    LoggerHandler.setKcGlobalInstanceId(null);
    this.loggerHandler = new LoggerHandler(this.name);
  }

  // test
  @Test
  public void testAllLogMessageNoInstanceIds() {
    testAllLogMessagesRunner("");
  }

  @Test
  public void testAllLogMessageKcGlobalInstanceId() {
    LoggerHandler.setKcGlobalInstanceId(this.kcGlobalInstanceId);
    MockitoAnnotations.initMocks(this);

    testAllLogMessagesRunner("[KC:" + kcGlobalInstanceId.toString() + "] ");
  }

  @Test
  public void testAllLogMessageLoggingInstanceId() {
    this.loggerHandler = new LoggerHandler(this.name);
    this.loggerHandler.setLoggerInstanceIdTag(loggerInstanceIdDescriptor, loggerInstanceId);
    MockitoAnnotations.initMocks(this);

    testAllLogMessagesRunner("[" + loggerInstanceIdDescriptor + ":" + loggerInstanceId + "] ");
  }

  @Test
  public void testAllLogMessageAllInstanceIds() {
    this.loggerHandler = new LoggerHandler(this.name);
    LoggerHandler.setKcGlobalInstanceId(this.kcGlobalInstanceId);
    this.loggerHandler.setLoggerInstanceIdTag(loggerInstanceIdDescriptor, loggerInstanceId);
    MockitoAnnotations.initMocks(this);

    testAllLogMessagesRunner("[KC:" + kcGlobalInstanceId.toString() + "|" + loggerInstanceIdDescriptor + "] ");
  }

  @Test
  public void testInvalidUuid() {
    this.loggerHandler = new LoggerHandler(this.name);
    MockitoAnnotations.initMocks(this);

    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    this.loggerHandler.info(this.msg);
    Mockito.verify(logger, Mockito.times(1)).info(Utils.formatLogMessage(this.expectedMsg));
  }

  @Test
  public void testInvalidDescriptor() {
    this.loggerHandler = new LoggerHandler(this.name);
    MockitoAnnotations.initMocks(this);

    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    this.loggerHandler.info(this.msg);
    Mockito.verify(logger, Mockito.times(1)).info(Utils.formatLogMessage(this.expectedMsg));
  }

  @Test
  public void testLongDescriptor() {
    String longDescriptor = "looooooooooooooooong descriptor yayay";
    this.loggerHandler = new LoggerHandler(this.name);
    this.loggerHandler.setLoggerInstanceIdTag(longDescriptor, loggerInstanceId);
    MockitoAnnotations.initMocks(this);

    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    this.loggerHandler.info(this.msg);
    Mockito.verify(logger, Mockito.times(1))
        .info(
            Utils.formatLogMessage(
                "["
                    + longDescriptor
                    + ":"
                    + loggerInstanceId.toString()
                    + "]"
                    + " "
                    + this.expectedMsg));
  }

  private void testAllLogMessagesRunner(String expectedTag) {
    this.testLogMessagesRunner(this.msg, expectedTag + this.expectedMsg);
    this.testLogMessagesWithFormattingRunner(
        this.formatMsg, this.msg, expectedTag + this.expectedFormattedMsg);
  }

  private void testLogMessagesRunner(String msg, String expectedMsg) {
    // info
    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    loggerHandler.info(msg);

    Mockito.verify(logger, Mockito.times(1)).info(Utils.formatLogMessage(expectedMsg));

    // trace
    Mockito.when(logger.isTraceEnabled()).thenReturn(true);
    loggerHandler.trace(msg);

    Mockito.verify(logger, Mockito.times(1)).trace(Utils.formatLogMessage(expectedMsg));

    // debug
    Mockito.when(logger.isDebugEnabled()).thenReturn(true);
    loggerHandler.debug(msg);

    Mockito.verify(logger, Mockito.times(1)).debug(Utils.formatLogMessage(expectedMsg));

    // warn
    Mockito.when(logger.isWarnEnabled()).thenReturn(true);
    loggerHandler.warn(msg);

    Mockito.verify(logger, Mockito.times(1)).warn(Utils.formatLogMessage(expectedMsg));

    // error
    Mockito.when(logger.isErrorEnabled()).thenReturn(true);
    loggerHandler.error(msg);

    Mockito.verify(logger, Mockito.times(1)).error(Utils.formatLogMessage(expectedMsg));
  }

  private void testLogMessagesWithFormattingRunner(
      String formatMsg, String msg, String expectedFormattedMsg) {
    // info
    Mockito.when(logger.isInfoEnabled()).thenReturn(true);
    loggerHandler.info(formatMsg, msg);

    Mockito.verify(logger, Mockito.times(1)).info(Utils.formatLogMessage(expectedFormattedMsg));

    // trace
    Mockito.when(logger.isTraceEnabled()).thenReturn(true);
    loggerHandler.trace(formatMsg, msg);

    Mockito.verify(logger, Mockito.times(1)).trace(Utils.formatLogMessage(expectedFormattedMsg));

    // debug
    Mockito.when(logger.isDebugEnabled()).thenReturn(true);
    loggerHandler.debug(formatMsg, msg);

    Mockito.verify(logger, Mockito.times(1)).debug(Utils.formatLogMessage(expectedFormattedMsg));

    // warn
    Mockito.when(logger.isWarnEnabled()).thenReturn(true);
    loggerHandler.warn(formatMsg, msg);

    Mockito.verify(logger, Mockito.times(1)).warn(Utils.formatLogMessage(expectedFormattedMsg));

    // error
    Mockito.when(logger.isErrorEnabled()).thenReturn(true);
    loggerHandler.error(formatMsg, msg);

    Mockito.verify(logger, Mockito.times(1)).error(Utils.formatLogMessage(expectedFormattedMsg));
  }
}
