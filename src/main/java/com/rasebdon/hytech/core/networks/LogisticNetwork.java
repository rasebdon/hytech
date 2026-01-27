package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.IContainerHolder;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import java.util.*;

public abstract class LogisticNetwork<TContainer> implements IContainerHolder<TContainer> {

    protected final Set<LogisticPipeComponent<TContainer>> pipes = new HashSet<>();
    protected final List<LogisticTransferTarget<TContainer>> pullTargets = new ArrayList<>();
    protected final List<LogisticTransferTarget<TContainer>> pushTargets = new ArrayList<>();

    protected LogisticNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        resetPipes(initialPipes);
    }

    public Collection<LogisticPipeComponent<TContainer>> getPipes() {
        return Set.copyOf(pipes);
    }

    protected void resetPipes(Set<LogisticPipeComponent<TContainer>> newPipes) {
        pipes.clear();
        for (var pipe : newPipes) {
            pipes.add(pipe);
            pipe.assignNetwork(this);
        }
        rebuildTargets();
    }

    protected void detachPipe(LogisticPipeComponent<TContainer> pipe) {
        pipes.remove(pipe);
    }

    public void rebuildTargets() {
        pullTargets.clear();
        pushTargets.clear();

        for (var pipe : pipes) {
            for (var target : pipe.getTransferTargets()) {
                if (target == null || target.target() instanceof LogisticPipeComponent<TContainer>) {
                    continue;
                }

                var face = target.from();
                if (pipe.canReceiveFromFace(face)) {
                    pullTargets.add(target);
                }
                if (pipe.canExtractFromFace(face)) {
                    pushTargets.add(target);
                }
            }
        }
    }

    public abstract void pullFromTargets();

    public abstract void pushToTargets();
}
