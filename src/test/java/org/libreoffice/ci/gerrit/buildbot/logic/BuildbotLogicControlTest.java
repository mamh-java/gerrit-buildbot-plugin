package org.libreoffice.ci.gerrit.buildbot.logic;

import java.io.PrintWriter;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.libreoffice.ci.gerrit.buildbot.commands.TaskStatus;
import org.libreoffice.ci.gerrit.buildbot.config.BuildbotConfig;
import org.libreoffice.ci.gerrit.buildbot.config.BuildbotProject;
import org.libreoffice.ci.gerrit.buildbot.config.TriggerStrategy;
import org.libreoffice.ci.gerrit.buildbot.model.GerritJob;
import org.libreoffice.ci.gerrit.buildbot.model.Os;
import org.libreoffice.ci.gerrit.buildbot.model.TbJobDescriptor;
import org.libreoffice.ci.gerrit.buildbot.model.TbJobResult;
import org.libreoffice.ci.gerrit.buildbot.utils.QueueUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class BuildbotLogicControlTest {

    BuildbotConfig config;
    BuildbotLogicControl control;
    static final String PROJECT = "FOO";
    static final String TB1 = "42";
    static final String TB2 = "43";
    static final String URL = "url";

    @Before
    public void setUp() throws Exception {
        config = new BuildbotConfig();
        config.setEmail("email@site");
        config.setForgeReviewerIdentity(true);
        ImmutableList.Builder<BuildbotProject> projects = ImmutableList.builder();
        BuildbotProject project = new BuildbotProject(PROJECT);
        project.setTriggerStrategy(TriggerStrategy.MANUALLY);
        projects.add(project);
        config.setProjects(projects.build());
        control = new BuildbotLogicControl(config);
        control.start();
        BasicConfigurator.configure();
    }

    @After
    public void tearDown() throws Exception {
        control.stop();
    }

    @Test()
    public void testFindJobByRevision() {
        String revId = "abcdefghijklmnopqrstuvwxyz";
        control.startGerritJob(PROJECT, "4711", "master", "4712", revId);
        GerritJob gerritJob = control.findJobByRevision(PROJECT, revId);
        Assert.assertNotNull(gerritJob);
    }
    @Test()
    public void testCompleteJob() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "abcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, TB1, false);
        Assert.assertNotNull(job);
        TbJobResult result = control.setResultPossible(job.getTicket(), TB1, TaskStatus.SUCCESS, URL);
        Assert.assertNotNull(result);
        job = control.launchTbJob(PROJECT, Os.Linux, branchSet, TB1, false);
        Assert.assertNull(job);
        job = control.launchTbJob(PROJECT, Os.Windows, branchSet, TB1, false);
        Assert.assertNotNull(job);
        result = control.setResultPossible(job.getTicket(), TB1, TaskStatus.SUCCESS, URL);
        job = control.launchTbJob(PROJECT, Os.Windows, branchSet, TB1, false);
        Assert.assertNull(job);
        job = control.launchTbJob(PROJECT, Os.MacOSX, branchSet, TB1, false);
        Assert.assertNotNull(job);
        Assert.assertFalse(result.getTbPlatformJob().getParent().allJobsReady());
        result = control.setResultPossible(job.getTicket(), TB1, TaskStatus.SUCCESS, URL);
        Assert.assertTrue(result.getTbPlatformJob().getParent().allJobsReady());
        dumpQueue();
    }

    @Test()
    public void testBoxMismatch() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "abcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, TB1, true);
        Assert.assertNotNull(job);
        TbJobResult result = control.setResultPossible(job.getTicket(), TB2, TaskStatus.SUCCESS, URL);
        Assert.assertNull(result);
        dumpQueue();
    }

    @Test()
    public void testFindProjectByTicket() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "abcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, TB1, false);
        Assert.assertEquals(PROJECT, control.findProjectByTicket(job.getTicket()));
    }

    @Test
    public void test2DiscardedTasks() {
        control.startGerritJob(PROJECT, "master", "4711", "4712", "abcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", false);
        Assert.assertNotNull(job);
        TbJobResult result = control.setResultPossible(job.getTicket(), "42", TaskStatus.FAILED, "url");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.getTbPlatformJob().getParent().allJobsReady());
        job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", false);
        Assert.assertNull(job);
        job = control.launchTbJob(PROJECT, Os.Windows, branchSet, "42", false);
        Assert.assertNull(job);
        job = control.launchTbJob(PROJECT, Os.MacOSX, branchSet, "42", false);
        Assert.assertNull(job);
        dumpQueue();
    }

    @Test
    public void test1CanceledTasks() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "a1bcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", false);
        Assert.assertNotNull(job);
        TbJobResult result = control.setResultPossible(job.getTicket(), "42", TaskStatus.SUCCESS, "url");
        Assert.assertNotNull(result);
        job = control.launchTbJob(PROJECT, Os.Windows, branchSet, TB1, false);
        Assert.assertNotNull(job);
        result = control.setResultPossible(job.getTicket(), TB1, TaskStatus.SUCCESS, URL);
        job = control.launchTbJob(PROJECT, Os.MacOSX, branchSet, TB1, false);
        Assert.assertNotNull(job);
        result = control.setResultPossible(job.getTicket(), TB1, TaskStatus.CANCELED, URL);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.getTbPlatformJob().getParent().allJobsReady());
        dumpQueue();
    }

    @Test
    public void testPeekTasks() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "a1bcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", true);
        Assert.assertNotNull(job);
        job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", true);
        Assert.assertNotNull(job);
        job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", false);
        Assert.assertNotNull(job);
        job = control.launchTbJob(PROJECT, Os.Linux, branchSet, "42", false);
        Assert.assertNull(job);
        job = control.launchTbJob(PROJECT, Os.Windows, branchSet, TB1, true);
        Assert.assertNotNull(job);
        job = control.launchTbJob(PROJECT, Os.MacOSX, branchSet, TB1, false);
        Assert.assertNotNull(job);
        dumpQueue();
    }

    @Test
    @Ignore
    public void testManyJobs() {
        String prefix = "a1bcdefghi";
        Set<String> branchSet = Sets.newHashSet();
        for (int i = 0; i < 100; i++) {
            control.startGerritJob(PROJECT, "4711", "master", "4712", prefix + i);
        }
        for (int i = 0; i < 50; i++) {
            for (Os p : Os.values()) {
                TbJobDescriptor job = control.launchTbJob(PROJECT, p, branchSet, "42", false);
                Assert.assertNotNull(job);
            }
        }
        dumpQueue();
    }

    @Test
    public void testStaleJob() {
        control.startGerritJob(PROJECT, "4711", "master", "4712", "a1bcdefghijklmnopqrstuvwxyz");
        Set<String> branchSet = Sets.newHashSet();
        TbJobDescriptor descr = control.launchTbJob(PROJECT, Os.Linux, branchSet, TB1, false);
        Assert.assertNotNull(descr);
        GerritJob job = control.findJobByChange(PROJECT, "4711");
        Assert.assertNotNull(job);
        job.handleStale(control.getTBQueueMap(PROJECT));
        Assert.assertTrue(job.isStale());
        TbJobResult result = control.setResultPossible(descr.getTicket(), TB1, TaskStatus.SUCCESS, URL);
        Assert.assertNotNull(result);
        dumpQueue();
    }

    private void dumpQueue() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        PrintWriter stdout = new PrintWriter(System.out);
        QueueUtils.dumpQueue(stdout, null, control, PROJECT, true);
        stdout.flush();
    }
}
