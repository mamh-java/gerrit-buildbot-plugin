/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This file is part of the LibreOffice project.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.ci.gerrit.buildbot.logic.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.libreoffice.ci.gerrit.buildbot.commands.TaskStatus;
import org.libreoffice.ci.gerrit.buildbot.logic.ProjectControl;
import org.libreoffice.ci.gerrit.buildbot.model.BuildbotPlatformJob;
import org.libreoffice.ci.gerrit.buildbot.model.GerritJob;
import org.libreoffice.ci.gerrit.buildbot.model.Platform;
import org.libreoffice.ci.gerrit.buildbot.model.TBBlockingQueue;
import org.libreoffice.ci.gerrit.buildbot.model.TbJobDescriptor;
import org.libreoffice.ci.gerrit.buildbot.model.TbJobResult;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;

public class ProjectControlImpl implements ProjectControl {

	private final Map<Platform, TBBlockingQueue> tbQueueMap = 
        new ConcurrentHashMap<Platform, TBBlockingQueue>();
	private final List<GerritJob> gerritJobList = 
        Collections.synchronizedList(new ArrayList<GerritJob>());

	public ProjectControlImpl() {
	}

	@Override
	public void start() {
		for (int i = 0; i < Platform.values().length; i++) {
            tbQueueMap.put(Platform.values()[i],
            				new TBBlockingQueue(Platform.values()[i]));
		}
	}
	
	@Override
	public void stop() {
		synchronized (gerritJobList) {
			for (GerritJob job : gerritJobList) {
				for (BuildbotPlatformJob task : job.getBuildbotList()) {
					task.setAbort(true);
				}
			}
            gerritJobList.clear();
			tbQueueMap.clear();
		}
	}
	
	private TBBlockingQueue getQueue(Platform p) {
		return tbQueueMap.get(p);
	}

	public void finishGerritJob(GerritJob job) {
		synchronized (gerritJobList) {
			gerritJobList.remove(job);
		}
	}

	public void startGerritJob(PatchSetCreatedEvent event) {
		synchronized (gerritJobList) {
			GerritJob job = new GerritJob(this, event.change.project, event.change.branch, event.patchSet.ref, event.patchSet.revision);
			startJob(job);
		}
	}

	public void startGerritJob(CommentAddedEvent event) {
		synchronized (gerritJobList) {
			GerritJob job = new GerritJob(this, event.change.project, event.change.branch, event.patchSet.ref, event.patchSet.revision);
			startJob(job);
		}
	}
	
	public void startGerritJob(Change change, PatchSet patchSet) {
		synchronized (gerritJobList) {
			GerritJob job = new GerritJob(this, change.getProject().get(), change.getDest().getShortName(), 
					patchSet.getRefName(), patchSet.getRevision().get());
			startJob(job);
		}
	}

	private void startJob(GerritJob job) {
		job.poulateTBPlatformQueueMap(tbQueueMap);
		gerritJobList.add(job);
		job.start();
	}

	public TbJobResult setResultPossible(String ticket, String boxId, TaskStatus status, String log) {
		synchronized (gerritJobList) {
			for (GerritJob job : gerritJobList) {
				TbJobResult jobResult = job.setResultPossible(ticket, boxId, log, status);
				if (jobResult != null) {
					if (job.allJobsReady()) {
						job.createTBResultList();
					}
					return jobResult;
				}
			}
		}
		return null;
	}

	public TbJobDescriptor launchTbJob(Platform platform, Set<String> branchSet, String box) {
		synchronized (gerritJobList) {
			TBBlockingQueue platformQueue = getQueue(platform);
	
			BuildbotPlatformJob tbJob = platformQueue.poll(branchSet);
			if (tbJob == null) {
				return null;
			}
			tbJob.createAndSetTicket(platform, box);
	
			TbJobDescriptor desc = new TbJobDescriptor(tbJob);
			return desc;
		}
	}

	public static void sleep(int i) {
		try {
			Thread.sleep(i);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public int getGerritJobsCount() {
		synchronized (gerritJobList) {
			return gerritJobList.size();
		}
	}

	public List<GerritJob> getGerritJobs() {
		return gerritJobList;
	}
	
	@Override
	public GerritJob findJobByRevision(String revision) {
		synchronized (gerritJobList) {
			for (GerritJob job : gerritJobList) {	
				if (job.getGerritRevision().equals(revision)) {
					return job;
				}
			}
		}
		return null;
	}
	
	public Map<Platform, TBBlockingQueue> getTbQueueMap() {
		return tbQueueMap;
	}
}