package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.sync.ReconciliationContextKeys;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationEmptyTargetDecider implements JobExecutionDecider {

    public static final String SKIP = "SKIP";
    public static final String PROCESS = "PROCESS";

    private final ReconciliationProperties properties;

    public ReconciliationEmptyTargetDecider(ReconciliationProperties properties) {
        this.properties = properties;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        Boolean targetEmpty = jobExecution.getExecutionContext().get(
                ReconciliationContextKeys.TARGET_EMPTY, Boolean.class);
        if (Boolean.TRUE.equals(targetEmpty) && properties.isSkipWhenTargetEmpty()) {
            return new FlowExecutionStatus(SKIP);
        }
        return new FlowExecutionStatus(PROCESS);
    }
}
