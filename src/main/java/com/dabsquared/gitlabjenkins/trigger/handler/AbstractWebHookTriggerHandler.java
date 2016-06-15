package com.dabsquared.gitlabjenkins.trigger.handler;

import com.dabsquared.gitlabjenkins.GitLabPushTrigger;
import com.dabsquared.gitlabjenkins.cause.CauseData;
import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.WebHook;
import com.dabsquared.gitlabjenkins.trigger.exception.NoRevisionToBuildException;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.util.LoggerUtil;
import com.dabsquared.gitlabjenkins.webhook.GitLabWebHook;
import com.dabsquared.gitlabjenkins.webhook.WebHookAction;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.plugins.git.RevisionParameterAction;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.HttpResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Robin Müller
 */
public abstract class AbstractWebHookTriggerHandler<H extends WebHook> implements WebHookTriggerHandler<H> {

    private static final Logger LOGGER = Logger.getLogger(AbstractWebHookTriggerHandler.class.getName());

    @Override
    public void handle(Job<?, ?> job, H hook, boolean ciSkip, BranchFilter branchFilter) {
        if (ciSkip && isCiSkip(hook)) {
            LOGGER.log(Level.INFO, "Skipping due to ci-skip.");
            return;
        }

        String targetBranch = getTargetBranch(hook);
        if (branchFilter.isBranchAllowed(targetBranch)) {
            LOGGER.log(Level.INFO, "{0} triggered for {1}.", LoggerUtil.toArray(job.getFullName(), getTriggerType()));
            scheduleBuild(job, createActions(job, hook));
        } else {
            LOGGER.log(Level.INFO, "branch {0} is not allowed", targetBranch);
        }
    }

    protected abstract String getTriggerType();

    protected abstract boolean isCiSkip(H hook);

    private Action[] createActions(Job<?, ?> job, H hook) {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(new CauseAction(new GitLabWebHookCause(retrieveCauseData(hook))));
        try {
            actions.add(createRevisionParameter(hook));
        } catch (NoRevisionToBuildException e) {
            LOGGER.log(Level.WARNING, "unknown handled situation, dont know what revision to build for req {0} for job {1}",
                    new Object[]{hook, (job != null ? job.getFullName() : null)});
        }
        return actions.toArray(new Action[actions.size()]);
    }

    protected abstract CauseData retrieveCauseData(H hook);

    protected abstract String getTargetBranch(H hook);

    protected abstract RevisionParameterAction createRevisionParameter(H hook) throws NoRevisionToBuildException;

    protected URIish retrieveUrIish(WebHook hook) {
        try {
            if (hook.getRepository() != null) {
                return new URIish(hook.getRepository().getUrl());
            }
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING, "could not parse URL");
        }
        return null;
    }

    private CauseData findCauseData(final List<? extends Action> actions) {
        for (final Action action: actions) {
            if (action instanceof CauseAction) {
                final CauseAction causeAction = (CauseAction) action;
                final GitLabWebHookCause webHookCause = causeAction.findCause(GitLabWebHookCause.class);
                return webHookCause.getData();
            }
        }
        return null;
    }

    private static boolean equals(final CauseData cd1, final CauseData cd2) {
        if (!cd1.getActionType().equals(cd2)) {
            LOGGER.log(Level.INFO, "action type");
        }

        if (cd1.getProjectId() != cd2.getProjectId()) {
            LOGGER.log(Level.INFO, "project id");
        }

        if (!cd1.getBranch().equals(cd2.getBranch())) {
            LOGGER.log(Level.INFO, "branch");
        }


        if (!cd1.getSourceBranch().equals(cd2.getSourceBranch()) {
            LOGGER.log(Level.INFO, "source branch");
        }

        if (!cd1.getUserName().equals(cd2.getUserName())) {
            LOGGER.log(Level.INFO, "user name");
        }

        if (!cd1.getUserEmail().equals(cd2.getUserEmail())) {
            LOGGER.log(Level.INFO, "user name");
        }

        if (!cd1.getSourceRepoHomepage().equals(cd2.getSourceRepoHomepage())) {
            LOGGER.log(Level.INFO, "sourceRepoHomepage");
        }

        this.sourceRepoName = checkNotNull(sourceRepoName, "sourceRepoName must not be null.");
        this.sourceNamespace = checkNotNull(sourceNamespace, "sourceNamespace must not be null.");
        this.sourceRepoUrl = sourceRepoUrl == null ? sourceRepoSshUrl : sourceRepoUrl;
        this.sourceRepoSshUrl = checkNotNull(sourceRepoSshUrl, "sourceRepoSshUrl must not be null.");
        this.sourceRepoHttpUrl = checkNotNull(sourceRepoHttpUrl, "sourceRepoHttpUrl must not be null.");
        this.mergeRequestTitle = checkNotNull(mergeRequestTitle, "mergeRequestTitle must not be null.");
        this.mergeRequestDescription = mergeRequestDescription == null ? "" : mergeRequestDescription;
        this.mergeRequestId = mergeRequestId;
        this.mergeRequestIid = mergeRequestIid;
        this.targetBranch = checkNotNull(targetBranch, "targetBranch must not be null.");
        this.targetRepoName = checkNotNull(targetRepoName, "targetRepoName must not be null.");
        this.targetNamespace = checkNotNull(targetNamespace, "targetNamespace must not be null.");
        this.targetRepoSshUrl = checkNotNull(targetRepoSshUrl, "targetRepoSshUrl must not be null.");
        this.targetRepoHttpUrl = checkNotNull(targetRepoHttpUrl, "targetRepoHttpUrl must not be null.");
        this.triggeredByUser = checkNotNull(triggeredByUser, "triggeredByUser must not be null.");
    }

    private void cancelScheduleJob(final Job<?, ?> job, final Action[] newActions) {

        LOGGER.log(Level.INFO, String.format("Checking if a job named %s already exists.", job.getName()));
        final Queue queue = Jenkins.getInstance().getQueue();
        final Item[] items = queue.getItems();

        for (final Item currentItem: items) {

            if (currentItem == null) {
                // skip this item
                LOGGER.log(Level.INFO, String.format("No item with ID %d found.", currentItem.getId()));
                continue;
            }

            if (!StringUtils.equals(currentItem.task.getName(), job.getName())) {
                    // skip other jobs
                    LOGGER.log(Level.INFO, String.format("Job '%s' is not the target one.", currentItem.task.getName()));
                    continue;
            }

            // retrieve cause data, if existing
            final List<Action> actionList = new ArrayList<Action>(Arrays.asList(newActions));
            final CauseData theCauseData = findCauseData(actionList);
            if (theCauseData == null) {
                LOGGER.log(Level.INFO, String.format("No cause data for job %s data found.", job.getName()));
                continue;
            }

            final List<? extends Action> allActions = currentItem.getAllActions();
            LOGGER.log(Level.INFO, String.format("%d actions for job scheduled.", allActions.size()));
            final CauseData oldCauseData = findCauseData(allActions);
            if (theCauseData == null) {
                LOGGER.log(Level.INFO, "No cause data for the current job found");
            }

            equals(oldCauseData, theCauseData);

            if (!theCauseData.equals(oldCauseData)) {
                LOGGER.log(Level.INFO, "CauseData are not equal");
            }

            if (!StringUtils.equals(theCauseData.getBranch(), oldCauseData.getBranch())) {
                LOGGER.log(Level.INFO, "CauseData contain different branches");
                continue;
            }

            if (!StringUtils.equals(theCauseData.getSourceBranch(), oldCauseData.getSourceBranch())) {
                LOGGER.log(Level.INFO, "CauseData contain different source branches");
                continue;
            }

            try {
                LOGGER.log(Level.INFO, String.format("Cancelling scheduled build job '%s'", job.getName()));
                final HttpResponse response = queue.doCancelItem(currentItem.getId());
            } catch (IOException e) {
                LOGGER.log(Level.INFO, String.format("Exception during job cancellation: %s", e.getMessage()));
            } catch (ServletException e) {
                LOGGER.log(Level.INFO, String.format("Exception during job cancellation: %s", e.getMessage()));
            }
        }
    }

    private void scheduleBuild(Job<?, ?> job, Action[] actions) {

        LOGGER.log(Level.INFO, "Searching for existing job to cancel.");
        cancelScheduleJob(job, actions);

        int projectBuildDelay = 0;
        if (job instanceof ParameterizedJob) {
            ParameterizedJob abstractProject = (ParameterizedJob) job;
            if (abstractProject.getQuietPeriod() > projectBuildDelay) {
                projectBuildDelay = abstractProject.getQuietPeriod();
            }
        }
        retrieveScheduleJob(job).scheduleBuild2(projectBuildDelay, actions);
    }

    private ParameterizedJobMixIn retrieveScheduleJob(final Job<?, ?> job) {
        // TODO 1.621+ use standard method
        return new ParameterizedJobMixIn() {
            @Override
            protected Job asJob() {
                return job;
            }
        };
    }
}
