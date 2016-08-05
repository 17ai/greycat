package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.plugin.AbstractTaskAction;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Task;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

class ActionIsolatedSubTask extends AbstractTaskAction {

    private final Task _subTask;

    ActionIsolatedSubTask(final Task p_subTask) {
        super();
        _subTask = p_subTask;
    }

    @Override
    public void eval(final TaskContext context) {
        final TaskResult previous = context.result();
        _subTask.executeFrom(context, previous, SchedulerAffinity.SAME_THREAD, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult subTaskResult) {
                if(subTaskResult != null){
                    subTaskResult.free();
                }
                context.continueWith(previous);
            }
        });
    }

    @Override
    public String toString() {
        return "subTask()";
    }

}
