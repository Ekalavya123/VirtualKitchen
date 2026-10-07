package com.processVisualisation.virtualKitchen.ai.workflow.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Overall lifecycle of a {@link RecipeAiWorkflow}.
 * <pre>
 *   GENERATING_PROCESS ──► WAITING_FOR_APPROVAL ──(user approves)──► RUNNING_DOWNSTREAM_TASKS
 *          │                       │                                          │
 *          ▼                       ▼                                          ▼
 *        FAILED                CANCELLED               COMPLETED / PARTIALLY_COMPLETED / FAILED
 * </pre>
 * A workflow without the PROCESS task starts at WAITING_FOR_APPROVAL (the user approves the
 * recipe's existing process). The job-level QUEUED state is not repeated here: while the generation
 * job is queued the workflow is GENERATING_PROCESS and its PROCESS task is QUEUED. CREATED is the
 * brief state between inserting the workflow and starting its first job.
 */
public enum RecipeAiWorkflowStatus {
    CREATED,
    GENERATING_PROCESS,
    WAITING_FOR_APPROVAL,
    RUNNING_DOWNSTREAM_TASKS,
    /** Some tasks succeeded, at least one failed; the failed ones can be retried on their own. */
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<RecipeAiWorkflowStatus> TERMINAL = EnumSet.of(PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
