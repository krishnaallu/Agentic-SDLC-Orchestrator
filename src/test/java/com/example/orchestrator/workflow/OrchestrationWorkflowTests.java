package com.example.orchestrator.workflow;

import com.example.orchestrator.run.TaskBlueprint;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationWorkflowTests {
    private static final String TASK_QUEUE = "workflow-test-queue";

    @Test
    void waitsForApprovalThenExecutesIndependentNodesInParallel() throws Exception {
        TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance();
        TestActivities activities = new TestActivities();
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();

        try {
            OrchestrationWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                    OrchestrationWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId("parallel-test-run").setTaskQueue(TASK_QUEUE).build());
            WorkflowClient.start(workflow::execute, "parallel-test-run");
            workflow.recordDecision(true);
            assertThat(activities.artifactReviewReady.await(3, TimeUnit.SECONDS)).isTrue();
            workflow.recordArtifactDecision(true);
            WorkflowStub.fromTyped(workflow).getResult(Void.class);

            assertThat(activities.started).isTrue();
            assertThat(activities.failed).isNull();
            assertThat(activities.completed).containsExactlyInAnyOrder(
                    "requirements", "architecture", "implementation", "tests", "documentation", "release");
            assertThat(activities.releaseSawAllBranches).isTrue();
                assertThat(activities.awaitingReview).isTrue();
            assertThat(activities.artifactsAccepted).isTrue();
        } finally {
            environment.close();
        }
    }

    @Test
    void rejectedPlanDoesNotStartAnyTask() {
        TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance();
        TestActivities activities = new TestActivities();
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();

        try {
            OrchestrationWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                    OrchestrationWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId("rejected-test-run").setTaskQueue(TASK_QUEUE).build());
            WorkflowClient.start(workflow::execute, "rejected-test-run");
            workflow.recordDecision(false);
            WorkflowStub.fromTyped(workflow).getResult(Void.class);

            assertThat(activities.started).isFalse();
            assertThat(activities.completed).isEmpty();
        } finally {
            environment.close();
        }
    }

    @Test
    void ambiguousRunPausesUntilHumanClarifies() throws Exception {
        TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance();
        TestActivities activities = new TestActivities(true);
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();

        try {
            OrchestrationWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                    OrchestrationWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId("ambiguous-test-run").setTaskQueue(TASK_QUEUE).build());
            WorkflowClient.start(workflow::execute, "ambiguous-test-run");
            workflow.recordDecision(true);
            assertThat(activities.clarificationRequested.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(activities.completed).containsExactly("ambiguity-analysis");

            workflow.recordClarification("Omitted expiry means no expiration; reject past dates; codes are never reused.");
            assertThat(activities.artifactReviewReady.await(3, TimeUnit.SECONDS)).isTrue();
            workflow.recordArtifactDecision(true);
            WorkflowStub.fromTyped(workflow).getResult(Void.class);

            assertThat(activities.clarificationRecorded).contains("codes are never reused");
            assertThat(activities.completed).contains("architecture", "implementation", "tests", "documentation", "release");
        } finally {
            environment.close();
        }
    }

    @Test
    void retriesTransientTaskFailureAndContinues() throws Exception {
        TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance();
        TestActivities activities = new TestActivities(false, 2);
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();

        try {
            OrchestrationWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                    OrchestrationWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId("retry-test-run").setTaskQueue(TASK_QUEUE).build());
            WorkflowClient.start(workflow::execute, "retry-test-run");
            workflow.recordDecision(true);
            assertThat(activities.artifactReviewReady.await(10, TimeUnit.SECONDS)).isTrue();
            workflow.recordArtifactDecision(true);
            WorkflowStub.fromTyped(workflow).getResult(Void.class);

            assertThat(activities.requirementsAttempts).isEqualTo(3);
            assertThat(activities.completed).contains("requirements");
            assertThat(activities.failed).isNull();
        } finally {
            environment.close();
        }
    }

    private static final class TestActivities implements OrchestrationActivities {
        private final CountDownLatch parallelBranches = new CountDownLatch(3);
        private final CountDownLatch artifactReviewReady = new CountDownLatch(1);
        private final CountDownLatch clarificationRequested = new CountDownLatch(1);
        private final Set<String> completed = ConcurrentHashMap.newKeySet();
        private final boolean ambiguous;
        private final int transientFailures;
        private volatile boolean started;
        private volatile boolean releaseSawAllBranches;
        private volatile boolean awaitingReview;
        private volatile boolean artifactsAccepted;
        private volatile String failed;

        private volatile String clarificationRecorded;
        private volatile int requirementsAttempts;

        private TestActivities() {
            this(false);
        }

        private TestActivities(boolean ambiguous) {
            this(ambiguous, 0);
        }

        private TestActivities(boolean ambiguous, int transientFailures) {
            this.ambiguous = ambiguous;
            this.transientFailures = transientFailures;
        }

        @Override
        public List<TaskBlueprint> loadPlan(String runId) {
                return List.of(
                    node(ambiguous ? "ambiguity-analysis" : "requirements"),
                    node("architecture", ambiguous ? "ambiguity-analysis" : "requirements"),
                    node("implementation", "architecture"),
                    node("tests", "architecture"),
                    node("documentation", "architecture"),
                    node("release", "implementation", "tests", "documentation")
            );
        }

        @Override
        public void markRunStarted(String runId) {
            started = true;
        }

        @Override
        public void markAwaitingClarification(String runId) {
            clarificationRequested.countDown();
        }

        @Override
        public void recordClarification(String runId, String clarification) {
            clarificationRecorded = clarification;
        }

        @Override
        public void executeTask(String runId, String taskKey) {
            if (taskKey.equals("requirements")) {
                requirementsAttempts++;
                if (requirementsAttempts <= transientFailures) {
                    throw new IllegalStateException("Simulated transient task failure");
                }
            }
            if (Set.of("implementation", "tests", "documentation").contains(taskKey)) {
                parallelBranches.countDown();
                try {
                    if (!parallelBranches.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Independent branches did not run concurrently");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            if (taskKey.equals("release")) {
                releaseSawAllBranches = completed.containsAll(Set.of("implementation", "tests", "documentation"));
            }
            completed.add(taskKey);
        }

        @Override
        public void markRunReadyForReview(String runId) {
            awaitingReview = true;
            artifactReviewReady.countDown();
        }

        @Override
        public void finalizeArtifactReview(String runId, boolean accepted) {
            artifactsAccepted = accepted;
        }

        @Override
        public void markRunFailed(String runId, String reason) {
            failed = reason;
        }

        private static TaskBlueprint node(String key, String... dependencies) {
            return new TaskBlueprint(key, key, key, List.of(dependencies));
        }
    }
}