/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This file is part of the LibreOffice project.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.ci.gerrit.buildbot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.libreoffice.ci.gerrit.buildbot.commands.TaskStatus;
import org.libreoffice.ci.gerrit.buildbot.logic.impl.LogicControlImpl;

public class GerritJob implements Runnable {
	String gerritBranch;
	String gerritRef;
	String gerritRevision;
	Thread thread;
	String id;
	long startTime;

	final List<BuildbotPlatformJob> tinderBoxThreadList = Collections
			.synchronizedList(new ArrayList<BuildbotPlatformJob>());
	List<TbJobResult> tbResultList;
	LogicControlImpl control;

	public GerritJob(LogicControlImpl control, String gerritBranch,
			String gerritRef, String gerritRevision) {
		this.control = control;
		this.gerritBranch = gerritBranch;
		this.gerritRef = gerritRef;
		this.gerritRevision = gerritRevision;
		this.id = abbreviate(gerritRevision);
		this.startTime = System.currentTimeMillis();
	}

    /** Obtain a shorter version of this key string, using a leading prefix. */
    public String abbreviate(String s) {
      return s.substring(0, Math.min(s.length(), 9));
    }

	
	public void start() {
		thread = new Thread(this, "name");
		thread.start();
	}

	public String getGerritBranch() {
		return gerritBranch;
	}

	public boolean allJobsReady() {
		boolean done = true;
		for (BuildbotPlatformJob tbJob : tinderBoxThreadList) {
			if (!tbJob.isReady()) {
				done = false;
			}
		}
		return done;
	}

	@Override
	public void run() {

		boolean done = false;
		while (!done) {
			done = true;

			for (BuildbotPlatformJob tbJob : tinderBoxThreadList) {
				if (!tbJob.isReady()) {
					done = false;
				}
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}
		control.finishGerritJob(this);
	}

	public void createTBResultList() {
		tbResultList = new ArrayList<TbJobResult>();
		// GET AND REGISTER RESULTS
		for (BuildbotPlatformJob tbJob : tinderBoxThreadList) {
			tbResultList.add(tbJob.getResult());
		}
	}

	public void poulateTBPlatformQueueMap(
			Map<Platform, TBBlockingQueue> tbQueueMap) {
		for (int i = 0; i < Platform.values().length; i++) {
			Platform platform = Platform.values()[i];
			initPlatformJob(tbQueueMap, platform);
		}
	}

	private void initPlatformJob(Map<Platform, TBBlockingQueue> tbQueueMap,
			Platform platform) {
		BuildbotPlatformJob tbJob = new BuildbotPlatformJob(this, platform);
		tinderBoxThreadList.add(tbJob);
		tbQueueMap.get(platform).add(tbJob);
		tbJob.start();
	}

	public String getId() {
		return id;
	}

	BuildbotPlatformJob getTbJob(String ticket) {
		for (BuildbotPlatformJob job : tinderBoxThreadList) {
			if (!job.isStarted()) {
				continue;
			}

			if (job.getTicketString() != null
					&& job.getTicketString().equals(ticket)) {
				return job;
			}
		}
		return null;
	}

	public TbJobResult setResultPossible(String ticket, String log, TaskStatus status) {
		BuildbotPlatformJob job = getTbJob(ticket);
		if (job != null) {
			if (job.getResult() != null) {
                // result already set: ignore
                return null;
            }
			
			// Before we report a status back, check different strategies/optimisations
			// 1. if status is failed, then discard all pending tasks.
			if (status.isFailed()) {
				for (BuildbotPlatformJob job2 : tinderBoxThreadList) {
					if (job2.getTicketString() != null
						&& job2.getTicketString().equals(ticket)) {
						// skip the same task
						continue;
					}
					// discard pending tasks
					if (job2.isDiscardable()) {
						job2.discard();
					}
				}
			}
			
            TbJobResult jobResult = job.createResult(log, status);
         
         	// 2. if status is canceled, the reschedule a new task for the same platform
            // reuse the same id and drop the old task from the list (replace it)
         	if (status.isCanceled()) {
         		// important! remove it first and add a new instance
         		initPlatformJob(control.getTbQueueMap(), job.platform);
         		tinderBoxThreadList.remove(job);
         	}
			return jobResult;
		}
		return null;
	}

	public long getStartTime() {
		return startTime;
	}

	public List<BuildbotPlatformJob> getBuildbotList() {
		return tinderBoxThreadList;
	}

	public String getGerritRef() {
		return gerritRef;
	}

	public List<TbJobResult> getTbResultList() {
		return tbResultList;
	}

	public String getGerritRevision() {
		return gerritRevision;
	}
}
