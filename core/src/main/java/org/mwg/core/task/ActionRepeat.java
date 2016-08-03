package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.plugin.AbstractTaskAction;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.*;

import java.util.concurrent.atomic.AtomicInteger;

class ActionRepeat extends AbstractTaskAction {

    private final Task _subTask;

    private final String _iterationTemplate;

    ActionRepeat(final String p_iteration, final Task p_subTask) {
        super();
        this._subTask = p_subTask;
        this._iterationTemplate = p_iteration;
    }

    @Override
    public void eval(final TaskContext context) {
        final int nbIteration = TaskHelper.parseInt(context.template(_iterationTemplate));
        final ActionRepeat selfPointer = this;
        final AtomicInteger cursor = new AtomicInteger(0);
        final TaskResult results = context.newResult();
        results.allocate(nbIteration);
        if (nbIteration > 0) {
            final Callback[] recursiveAction = new Callback[1];
            recursiveAction[0] = new Callback<TaskResult>() {
                @Override
                public void on(final TaskResult res) {
                    int current = cursor.getAndIncrement();
                    if (res != null && res.size() == 1) {
                        results.set(current, res.get(0));
                    } else {
                        results.set(current, res);
                    }
                    int nextCursor = current + 1;
                    if (nextCursor == nbIteration) {
                        context.continueWith(results);
                    } else {
                        //recursive call
                        selfPointer._subTask.executeFrom(context, context.wrap(nextCursor), SchedulerAffinity.SAME_THREAD, recursiveAction[0]);
                    }
                }
            };
            _subTask.executeFrom(context, context.wrap(cursor.get()), SchedulerAffinity.SAME_THREAD, recursiveAction[0]);
        } else {
            context.continueWith(results);
        }
    }

    @Override
    public String toString() {
        return "repeat(\'" + _iterationTemplate + "\')";
    }

}
