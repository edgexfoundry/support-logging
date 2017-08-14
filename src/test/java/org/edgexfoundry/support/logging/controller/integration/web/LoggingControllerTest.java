/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: support-logging
 * @author: Jude Hung, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.support.logging.controller.integration.web;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.edgexfoundry.support.logging.controller.impl.LoggingControllerImpl;
import org.edgexfoundry.support.logging.dao.LogEntryDAO;
import org.edgexfoundry.support.logging.dao.MDC_ENUM_CONSTANTS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import org.edgexfoundry.EdgeXSupportLoggingApplication;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.support.domain.logging.LogEntry;
import org.edgexfoundry.support.logging.service.LoggingService;
import org.edgexfoundry.test.category.RequiresMongoDB;
import org.edgexfoundry.test.category.RequiresSpring;
import org.edgexfoundry.test.category.RequiresWeb;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = EdgeXSupportLoggingApplication.class)
@WebAppConfiguration("src/test/resources")
@Category({RequiresMongoDB.class, RequiresSpring.class, RequiresWeb.class})
public class LoggingControllerTest {

  @Autowired
  private ApplicationContext context;

  @Autowired
  private LoggingControllerImpl controller;

  @Autowired
  private LoggingService service;

  @Autowired
  @Qualifier("serviceDAO")
  private LogEntryDAO logEntryDAO;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Before
  public void setUp() throws Exception {
    mongoTemplate.remove(new Query(), "logEntry");
  }

  @After
  public void cleanup() throws Exception {
    mongoTemplate.remove(new Query(), "logEntry");
    changeControllerService(true);
  }

  @Test
  public void testAddLogEntry() throws InterruptedException {
    LogEntry entry = buildLogEntry("testService", Level.DEBUG,
        new String[] {"testLabel1", "testLabel2"}, "test log message from testcase.");
    verifyAddLogEntry(entry);
  }

  @Test
  public void testGetLogEntries() throws InterruptedException {
    LogEntry debug = buildLogEntry("debugService", Level.DEBUG, new String[] {"debug1", "debug2"},
        "test log message for debug.");
    LogEntry info = buildLogEntry("infoService", Level.INFO,
        new String[] {"info1", "info2", "info3"}, "test log message for info.");
    LogEntry warn = buildLogEntry("warnService", Level.WARN, new String[] {"warn1"},
        "test log message for warn.");
    LogEntry error = buildLogEntry("errorService", Level.ERROR,
        new String[] {"error1", "error2", "error3", "error4"}, "test log message for error.");
    LogEntry trace = buildLogEntry("traceService", Level.TRACE,
        new String[] {"trace1", "trace2", "trace3", "trace4", "trace5"},
        "test log message for trace.");
    verifyAddLogEntry(debug);
    verifyAddLogEntry(info);
    verifyAddLogEntry(warn);
    verifyAddLogEntry(error);
    verifyAddLogEntry(trace); // traces will not be persisted
    List<LogEntry> logEntries = controller.getLogEntries(10);
    assertTrue("Expect 4 but got " + logEntries.size() + " logEntries.", logEntries.size() == 4);
  }

  private void verifyAddLogEntry(LogEntry entry) throws InterruptedException {
    ResponseEntity<?> result = controller.addLogEntry(entry);
    HttpStatus status = result.getStatusCode();
    assertNotNull("HttpStatus is null.", status);
    assertTrue("HttpStatus is not 2xx.", status.is2xxSuccessful());
    Long accepted = (Long) result.getBody();
    assertTrue("accepted timestamp is zero or less.", accepted > 0);
    String loggingPersistence = context.getEnvironment().getProperty("logging.persistence");
    Thread.sleep(3000);
    if (loggingPersistence.equals("mongodb") && Level.TRACE != entry.getLogLevel()) {
      verifyMongoPersistence(entry);
    }
  }

  @Test(expected = ServiceException.class)
  public void testAddLogEntryException() throws Exception {
    // changeServiceDao(false);
    changeControllerService(false);
    LogEntry entry = new LogEntry();
    entry.setOriginService("testService");
    entry.setLabels(new String[] {"testLabel1", "testLabel2"});
    entry.setLogLevel(Level.DEBUG);
    entry.setMessage("test log message from testcase.");
    controller.addLogEntry(entry);
  }

  private LogEntry buildLogEntry(String originService, Level LogLevel, String[] labels,
      String message) {
    LogEntry entry = new LogEntry();
    entry.setOriginService(originService);
    entry.setLabels(labels);
    entry.setLogLevel(LogLevel);
    entry.setMessage(message);
    return entry;
  }

  private void verifyMongoPersistence(LogEntry entry) {
    List<LogEntry> result =
        mongoTemplate.find(new Query().addCriteria(buildCriteria(entry)), LogEntry.class);
    assertTrue("Expect to find one logEntry in MongoDB but none.", result.size() == 1);
  }

  private Criteria buildCriteria(LogEntry entry) {
    Criteria result = Criteria.where(MDC_ENUM_CONSTANTS.ORIGINSERVICE.getValue())
        .is(entry.getOriginService()).and(MDC_ENUM_CONSTANTS.LOGLEVEL.getValue())
        .is(entry.getLogLevel()).and(MDC_ENUM_CONSTANTS.MESSAGE.getValue()).is(entry.getMessage())
        .and(MDC_ENUM_CONSTANTS.LABELS.getValue()).in((Object[]) entry.getLabels());
    return result;
  }

  // use Java reflection to change service's DAO
  /*
   * private void changeServiceDao(boolean isReset) throws Exception { Class<?> serviceClass =
   * service.getClass(); Field temp = serviceClass.getDeclaredField("logEntryDAO");
   * temp.setAccessible(true); if(isReset){ temp.set(service, logEntryDAO); } else {
   * temp.set(service, null); }
   * 
   * }
   */

  // use Java reflection to change service's DAO
  private void changeControllerService(boolean isReset) throws Exception {
    Class<?> controllerClass = controller.getClass();
    Field temp = controllerClass.getDeclaredField("service");
    temp.setAccessible(true);
    if (isReset) {
      temp.set(controller, service);
    } else {
      temp.set(controller, null);
    }

  }

  /*
   * // use Java reflection to unset controller's tempalte private void unsetControllerMAXLIMIT()
   * throws Exception { Class<?> controllerClass = controller.getClass(); Field temp =
   * controllerClass.getDeclaredField(MAXLIMIT); temp.setAccessible(true); temp.set(controller, 0);
   * }
   * 
   * // use Java reflection to reset controller's template private void resetControllerMAXLIMIT()
   * throws Exception { Class<?> controllerClass = controller.getClass(); Field temp =
   * controllerClass.getDeclaredField(MAXLIMIT); temp.setAccessible(true); temp.set(controller,
   * 1000); }
   */

}
