/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This file is part of the LibreOffice project.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.ci.gerrit.buildbot.model;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.libreoffice.ci.gerrit.buildbot.commands.TaskStatus;
import org.libreoffice.ci.gerrit.buildbot.logic.impl.ProjectControlImpl;

public class BuildbotPlatformJob implements Runnable {
	Ticket ticket;
	boolean abort;
	AtomicBoolean started;
	AtomicBoolean ready;
	GerritJob parent;
	Thread thread;
	Os platform;
	TbJobResult result;
	long startTime;
	// TB id
	private String box;

	public BuildbotPlatformJob(GerritJob parent, Os platform) {
		this.platform = platform;
		started = new AtomicBoolean();
		ready = new AtomicBoolean();
		thread = new Thread(this, "TBPlatformJob " + parent.getGerritBranch()
				+ parent.getId() + " " + platform.name());
		this.parent = parent;
	}

	@Override
	public String toString() {
		return "BuildbotPlatformJob " + parent.getGerritBranch() + " "
				+ parent.getId() + " " + platform.name();
	}

	void log(String s) {
		// System.out.println( toString() + " " + s );
	}

	public void run() {

		log("Waiting for StartLock");
		while (!abort) {
			if (started.get()) {
				break;
			}
			ProjectControlImpl.sleep(100);
		}

		log("TBJob Waiting for ReadyLock");
		while (!abort) {
			if (ready.get()) {
				break;
			}
			ProjectControlImpl.sleep(100);
		}

		log("TBJob ready");

	}

	public String testBuildOnly(Os tbPlatform) {
	    ticket = new Ticket(parent.getId(), tbPlatform);
	    return ticket.toString();
	}

	public String createAndSetTicket(Os tbPlatform, String box) {
		ticket = new Ticket(parent.getId(), tbPlatform);
		this.box = box;
		started.set(true);
		startTime = System.currentTimeMillis();
		return ticket.toString();
	}

	public TbJobResult createResult(String log, TaskStatus status, String boxId, Set<BuildbotPlatformJob> discardedTasks) {
		result = new TbJobResult(this, ticket.getId(), platform, status, log, boxId, discardedTasks);
		ready.set(true);

		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return result;
	}

	public TbJobResult discard() {
		assert (started.get() == false);
		assert (ready.get() == false);
		result = new TbJobResult(this, StringUtils.EMPTY, platform, TaskStatus.DISCARDED, null, null, null);
		abort = true;

		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public void setAbort(boolean abort) {
		this.abort = abort;
	}

	public GerritJob getParent() {
		return parent;
	}

	public boolean isReady() {
		if (!thread.isAlive()) {
			try {
				thread.join();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	public String getTicketString() {
		if (ticket == null) {
			return null;
		}
		return ticket.toString();
	}

	public TbJobResult getResult() {
		return result;
	}

	public void start() {
		thread.start();
	}

	public boolean isDiscardable() {
		return !isStarted() && result == null;
	}
	
	public boolean isStarted() {
		return started.get();
	}
	
	public Ticket getTicket() {
		return ticket;
	}

	public String getPlatformString() {
		return platform.name();
	}
	
	public long getStartTime() {
		return startTime;
	}
	
	public Os getPlatform() {
		return platform;
	}

	public String getTinderboxId() {
		return box;
	}
}
