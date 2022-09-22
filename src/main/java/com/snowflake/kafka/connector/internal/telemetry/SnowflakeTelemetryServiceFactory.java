package com.snowflake.kafka.connector.internal.telemetry;

import com.snowflake.kafka.connector.internal.EnableLogging;

import java.sql.Connection;
import java.util.UUID;

/**
 * Factory class which produces the telemetry service which essentially has a telemetry client
 * instance.
 */
public class SnowflakeTelemetryServiceFactory {

  public static SnowflakeTelemetryServiceBuilder builder(Connection conn, UUID kcGlobalInstanceId) {
    return new SnowflakeTelemetryServiceBuilder(conn, kcGlobalInstanceId);
  }

  /** Builder for TelemetryService */
  public static class SnowflakeTelemetryServiceBuilder extends EnableLogging {
    private final SnowflakeTelemetryService service;

    /** @param conn snowflake connection is required for telemetry service */
    public SnowflakeTelemetryServiceBuilder(Connection conn, UUID kcGlobalInstanceId) {
      this.service = new SnowflakeTelemetryServiceV1(conn, kcGlobalInstanceId);
    }

    /**
     * @param name connector name
     * @return builder instance
     */
    public SnowflakeTelemetryServiceBuilder setAppName(String name) {
      this.service.setAppName(name);
      return this;
    }

    /**
     * @param taskID taskId
     * @return builder instance
     */
    public SnowflakeTelemetryServiceBuilder setTaskID(String taskID) {
      this.service.setTaskID(taskID);
      return this;
    }

    public SnowflakeTelemetryService build() {
      return this.service;
    }
  }
}
