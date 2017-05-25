/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
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
 *
 * @microservice:  support-logging
 * @author: Jude Hung, Dell
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry.support.logging.dao;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.edgexfoundry.support.domain.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;

public abstract class BaseLogEntryDAO implements LogEntryDAO {

	private final static Logger logger = LoggerFactory.getLogger(BaseLogEntryDAO.class);
	
	@Value("${logging.color.enabled:false}")
	private Boolean ADD_COLOR;
	
	private Map<String, String> colors = new HashMap<String, String>();
	
	private String noColor = "\033[0m";    // ANSI default color format
	private String green   = "\033[1;32m"; // ANSI foreground green
	private String red     = "\033[1;31m"; // ANSI foreground red
	private String yellow  = "\033[1;33m"; // ANSI foreground yellow
	
	private int i = 33;
	
	private String generateColor() {
		i++;
		if (i == 38) // end of base 8 foreground colors
			i = 90;  // ANSI base 16 foreground colors
		if (i == 91) // skip high visibility RGY for differentiation
			i = 94;  // ANSI base 16 foreground colors
		if (i == 98) // end of base 16 foreground colors
			i = 44;  // ANSI base 8 background colors
		if (i == 48) // end of base 8 background colors
			i = 104; // start of base 16 background colors
		if (i == 108)// end of base 16 background colors
			i = 34;  // start of base 16 background colors
		return String.format("\033[0m\033[1;%dm", i); // generate ANSI color code
	}
	
	private String wrapMessage(LogEntry entry) {
		if (ADD_COLOR) {
			switch (entry.getLogLevel()) {
			case DEBUG:
			case INFO:
			case TRACE:
				System.out.print(green);
				break;
			case WARN:
				System.out.print(yellow);
				break;
			case ERROR:
			default:
				System.out.print(red);
				break;
			}
			return colors.get(entry.getOriginService()) + entry.getMessage() + noColor;
		} else 
			return entry.getMessage();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.edgexfoundry.support.logging.dao.LogEntryDAO#save(org.edgexfoundry.support.
	 * logging.domain.LogEntry)
	 */
	@Override
	public boolean save(LogEntry entry) {
		MDC.put(MDC_ENUM_CONSTANTS.CREATED.getValue(), Long.toString(entry.getCreated()));
		MDC.put(MDC_ENUM_CONSTANTS.ORIGINSERVICE.getValue(), entry.getOriginService());
		MDC.put(MDC_ENUM_CONSTANTS.LABELS.getValue(),
				null == entry.getLabels() ? "[]" : Arrays.toString(entry.getLabels()));
		Level level = entry.getLogLevel();
		if (!colors.containsKey(entry.getOriginService())) 
			colors.put(entry.getOriginService(), generateColor());
		boolean loggable = false;
		synchronized(logger) {
			switch (level) {
			case DEBUG:
				logger.debug(wrapMessage(entry));
				loggable = logger.isDebugEnabled();
				break;
			case INFO:
				logger.info(wrapMessage(entry));
				loggable = logger.isInfoEnabled();
				break;
			case WARN:
				logger.warn(wrapMessage(entry));
				loggable = logger.isWarnEnabled();
				break;
			case ERROR:
				logger.error(wrapMessage(entry));
				loggable = logger.isErrorEnabled();
				break;
			default:
				logger.trace(wrapMessage(entry));
				loggable = logger.isTraceEnabled();
				break;
			}
		}
		return loggable;
	}

}
