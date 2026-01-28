package com.rasebdon.hytech.core.networks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.rasebdon.hytech.core.components.IContainerHolder;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import java.util.*;

public abstract class LogisticNetwork<TContainer> implements IContainerHolder<TContainer> {

    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final Set<LogisticPipeComponent<TContainer>> pipes = new HashSet<>();
    protected final List<LogisticTransferTarget<TContainer>> pullTargets = new ArrayList<>();
    protected final List<LogisticTransferTarget<TContainer>> pushTargets = new ArrayList<>();

    protected LogisticNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        setPipes(initialPipes);
    }

    public Collection<LogisticPipeComponent<TContainer>> getPipes() {
        return Set.copyOf(pipes);
    }

    protected void setPipes(Set<LogisticPipeComponent<TContainer>> newPipes) {
        LOGGER.atInfo().log("Setting Network with %d Pipes", newPipes.size());
        pipes.clear();
        for (var pipe : newPipes) {
            pipes.add(pipe);
            pipe.assignNetwork(this);
        }
        rebuildTargets();
    }

    protected void addPipe(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Adding Pipe to Network");
        pipes.add(pipe);
        pipe.assignNetwork(this);
        rebuildTargets();
    }

    protected void removePipe(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Detatching Pipe");
        pipes.remove(pipe);
        pipe.assignNetwork(null);
        rebuildTargets();
    }

    public void rebuildTargets() {
        LOGGER.atInfo().log("Rebuilding Network Targets");
        pullTargets.clear();
        pushTargets.clear();

        for (var pipe : pipes) {
            for (var target : pipe.getTransferTargets()) {
                if (target == null || target.target() instanceof LogisticPipeComponent<TContainer>) {
                    continue;
                }

                var face = target.from();
                if (pipe.canPullFrom(face)) {
                    pullTargets.add(target);
                }
                if (pipe.canPushTo(face)) {
                    pushTargets.add(target);
                }
            }
        }

        LOGGER.atInfo().log("Network Rebuilt: %d PULL / %d PUSH Targets", pullTargets.size(), pushTargets.size());
    }

    public abstract void pullFromTargets();

    public abstract void pushToTargets();
}
