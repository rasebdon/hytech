package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.IContainerHolder;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import java.util.*;

public abstract class LogisticNetwork<
        TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
        TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
        TContainer
        > implements IContainerHolder<TContainer> {

    protected final Set<TPipe> pipes = new HashSet<>();
    protected final List<LogisticTransferTarget<TContainer>> pullTargets = new ArrayList<>();
    protected final List<LogisticTransferTarget<TContainer>> pushTargets = new ArrayList<>();

    protected LogisticNetwork(Set<TPipe> initialPipes) {
        resetPipes(initialPipes);
    }

    public Collection<TPipe> getPipes() {
        return Set.copyOf(pipes);
    }

    @SuppressWarnings("unchecked")
    protected void resetPipes(Set<TPipe> newPipes) {
        pipes.clear();
        for (var pipe : newPipes) {
            pipes.add(pipe);
            pipe.assignNetwork((TNetwork) this);
        }
        rebuildTargets();
    }

    protected void detachPipe(TPipe pipe) {
        pipes.remove(pipe);
    }

    public void rebuildTargets() {
        pullTargets.clear();
        pushTargets.clear();

        for (var pipe : pipes) {
            for (var target : pipe.getTransferTargets()) {
                if (target == null || target.target() instanceof LogisticPipeComponent<?, ?, TContainer>) {
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
