/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.splicemachine.dbTesting.system.sttest.utils;

/**
 * This class is used to give the information about the memory status
 */
import java.util.Date;
import java.io.*;

public class StStatus {
	static final int messageCount = 20;
	
	int cycles = 0;
	
	int currentThreads = 0;
	
	int currentMessage = 0;
	
	public String firstMessage = null;
	
	public String[] messages;
	
	public StStatus() {
		messages = new String[messageCount];
	}
	
	public void firstMessage(int Threadcount, Date d) {
		currentThreads = Threadcount;
		firstMessage = "starting: " + d.toString() + " threads: "
		+ currentThreads;
	}
	
	public void updateStatus() throws IOException {
		Date d = new Date();
		cycles++;
		int counter = currentMessage % messageCount;
		Runtime rt = Runtime.getRuntime();
		messages[counter] = "Total memory: " + rt.totalMemory()
		+ " free memory: " + rt.freeMemory() + " cycles: " + cycles
		+ " threads: " + currentThreads + " " + d;
		currentMessage++;
		//overwrite messages file with current set of messages
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(
		"Sttest.log")));
		out.println(firstMessage);
		for (int i = 0; i < messageCount; i++) {
			if (messages[i] != null)
				out.println(messages[i]);
		}
		out.flush();
		out.close();
	}
}