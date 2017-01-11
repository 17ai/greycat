package org.mwg.structure.action;

import org.mwg.Callback;
import org.mwg.DeferCounter;
import org.mwg.Node;
import org.mwg.plugin.Job;
import org.mwg.structure.tree.KDTree;
import org.mwg.task.*;

public class NTreeNearestNWithinRadius implements Action {

    public static String NAME = "nTreeNearestNWithinRadius";

    private final double[] _key;
    private final int _n;
    private final double _radius;

    public NTreeNearestNWithinRadius(final double[] key, final int n, final double radius) {
        this._key = key;
        this._n = n;
        this._radius = radius;
    }

    @Override
    public final void eval(final TaskContext ctx) {
        final TaskResult previousResult = ctx.result();
        final TaskResult<Node> nextResult = ctx.newResult();
        if (previousResult != null) {
            final DeferCounter defer = ctx.graph().newCounter(previousResult.size());
            final TaskResultIterator previousResultIt = previousResult.iterator();
            Object iter = previousResultIt.next();
            while (iter != null) {
                if (iter instanceof KDTree ) {
                    ((KDTree) iter).nearestNWithinRadius(_key, _n, _radius, new Callback<Node[]>() {
                        @Override
                        public void on(Node[] result) {
                            for (int i = 0; i < result.length; i++) {
                                if (result[i] != null) {
                                    nextResult.add(result[i]);
                                }
                            }
                            defer.count();
                        }
                    });
                } else {
                    defer.count();
                }
                iter = previousResultIt.next();
            }
            defer.then(new Job() {
                @Override
                public void run() {
                    ctx.continueWith(nextResult);
                }
            });
        } else {
            ctx.continueWith(nextResult);
        }
    }

    @Override
    public void serialize(StringBuilder builder) {
        throw new RuntimeException("Not supported yet!");
    }

    @Override
    public String toString() {
        return "nTreeNearestNWithinRadius(\'" + "\')";
    }

}