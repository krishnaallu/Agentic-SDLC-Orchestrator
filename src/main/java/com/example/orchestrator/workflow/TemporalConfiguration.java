package com.example.orchestrator.workflow;

import com.example.orchestrator.run.OrchestrationProperties;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "orchestrator.temporal", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OrchestrationProperties.class)
public class TemporalConfiguration {
    @Bean
    WorkflowServiceStubs workflowServiceStubs(OrchestrationProperties properties) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(properties.temporal().target())
                .build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    WorkerFactory orchestrationWorkerFactory(WorkflowClient client, OrchestrationActivities activities,
                                             OrchestrationProperties properties) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(properties.temporal().taskQueue());
        worker.registerWorkflowImplementationTypes(OrchestrationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return factory;
    }
}