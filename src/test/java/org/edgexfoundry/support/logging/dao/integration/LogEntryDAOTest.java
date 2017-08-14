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

package org.edgexfoundry.support.logging.dao.integration;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.test.context.junit4.SpringRunner;

import org.edgexfoundry.EdgeXSupportLoggingApplication;
import org.edgexfoundry.support.domain.logging.LogEntry;
import org.edgexfoundry.support.domain.logging.MatchCriteria;
import org.edgexfoundry.support.logging.dao.LogEntryDAO;
import org.edgexfoundry.support.logging.dao.MDC_ENUM_CONSTANTS;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EdgeXSupportLoggingApplication.class)
public abstract class LogEntryDAOTest {

  protected Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected LogEntryDAO logEntryDAO;

  @Before
  public void setUp() {
    cleanPersistence();
  }

  // @After
  public void tearDown() {
    cleanPersistence();
  }

  protected LogEntry buildLogEntry(String originService, Level LogLevel, String[] labels,
      String message) {
    LogEntry entry = new LogEntry();
    entry.setOriginService(originService);
    entry.setLabels(labels);
    entry.setLogLevel(LogLevel);
    entry.setMessage(message);
    return entry;
  }

  protected Criteria buildCriteria(LogEntry entry) {
    Criteria result = Criteria.where(MDC_ENUM_CONSTANTS.ORIGINSERVICE.getValue())
        .is(entry.getOriginService()).and(MDC_ENUM_CONSTANTS.LOGLEVEL.getValue())
        .is(entry.getLogLevel()).and(MDC_ENUM_CONSTANTS.MESSAGE.getValue()).is(entry.getMessage())
        .and(MDC_ENUM_CONSTANTS.LABELS.getValue()).in((Object[]) entry.getLabels());
    return result;
  }

  @Test
  public void testSaveLoggable() {
    LogEntry debug = buildLogEntry("debugService", Level.DEBUG, new String[] {"debug1", "debug2"},
        "unit test of testSaveLoggable!");
    logEntryDAO.save(debug);
    verifyPersistence(debug, true);
  }

  @Test
  public void testSaveUnloggable() {
    LogEntry trace = buildLogEntry("traceService", Level.TRACE,
        new String[] {"trace1", "trace2", "trace3", "trace4", "trace5"},
        "test log message for trace.");
    logEntryDAO.save(trace);
    verifyPersistence(trace, false);
  }

  @Test
  public void testAddRemoveThenAdd() {
    LogEntry debug = buildLogEntry("debugService", Level.DEBUG, new String[] {"debug1", "debug2"},
        "unit test of testSaveLoggable!");
    logEntryDAO.save(debug);
    verifyPersistence(debug, true);
    MatchCriteria criteria = new MatchCriteria();
    logEntryDAO.removeByCriteria(criteria);
    LogEntry info = buildLogEntry("infoService", Level.INFO,
        new String[] {"info1", "info2", "info3"}, "test log message for info.");
    logEntryDAO.save(info);
    verifyPersistence(info, true);
  }

  @Test
  public void testSaveFindThenRemoveByCriteria() {
    LogEntry debug = buildLogEntry("debugService", Level.DEBUG, new String[] {"debug1", "debug2"},
        "unit test of testSaveFindThenRemoveByCriteria for debug level.");
    LogEntry info = buildLogEntry("infoService", Level.INFO,
        new String[] {"info1", "info2", "info3"}, "test log message for info.");
    LogEntry warn = buildLogEntry("warnService", Level.WARN, new String[] {"warn1"},
        "test log message for warn.");
    LogEntry error = buildLogEntry("errorService", Level.ERROR,
        new String[] {"error1", "error2", "error3", "error4"}, "test log message for error.");
    LogEntry trace = buildLogEntry("traceService", Level.TRACE,
        new String[] {"trace1", "trace2", "trace3", "trace4", "trace5"},
        "test log message for trace.");
    logEntryDAO.save(debug);
    verifyPersistence(debug, true);
    logEntryDAO.save(info);
    verifyPersistence(info, true);
    logEntryDAO.save(warn);
    verifyPersistence(warn, true);
    logEntryDAO.save(error);
    verifyPersistence(error, true);
    // Verify if TRACE is not loggable due to logging.level.edgexfoundry.support.logging=DEBUG as
    // defined in test-mongodb.properties
    logEntryDAO.save(trace);
    verifyPersistence(trace, false);
    MatchCriteria criteria = new MatchCriteria();
    List<LogEntry> logEntriesFound = logEntryDAO.findByCriteria(criteria, -1);
    assertTrue("Expect 4 but got " + logEntriesFound.size() + " logEntries to be found.",
        logEntriesFound.size() == 4);
    List<LogEntry> logEntriesRemoved = logEntryDAO.removeByCriteria(criteria);
    assertTrue("Expect 4 but got " + logEntriesRemoved.size() + " logEntries to be removed.",
        logEntriesRemoved.size() == 4);
    logEntriesFound = logEntryDAO.findByCriteria(criteria, -1);
    assertTrue("Expect 0 but got " + logEntriesFound.size() + " logEntries to be found.",
        logEntriesFound.size() == 0);
  }

  abstract public void cleanPersistence();

  abstract public void verifyPersistence(LogEntry entry, boolean expectToBeSaved);

}
