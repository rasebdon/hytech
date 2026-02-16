package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.IContainerHolder;
import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class LogisticNetwork<TContainer> implements IContainerHolder<TContainer> {

    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final Set<LogisticPipeComponent<TContainer>> pipes = new HashSet<>();
    protected final List<LogisticContainerComponent<TContainer>> pullTargets = new ArrayList<>();
    protected final List<LogisticContainerComponent<TContainer>> pushTargets = new ArrayList<>();

    protected LogisticNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        setPipes(initialPipes);
    }

    public Set<LogisticPipeComponent<TContainer>> getPipes() {
        return Set.copyOf(pipes);
    }

    protected void setPipes(Set<LogisticPipeComponent<TContainer>> newPipes) {

        LOGGER.atInfo().log("Setting Network with %d Pipes", newPipes.size());

        // Detach old pipes
        for (var pipe : pipes) {
            if (!newPipes.contains(pipe)) {
                pipe.assignNetwork(null);
            }
        }

        pipes.clear();

        for (var pipe : newPipes) {
            pipes.add(pipe);
            pipe.assignNetwork(this);
        }

        rebuildTargets();
    }

    protected void addPipe(LogisticPipeComponent<TContainer> pipe) {

        LOGGER.atInfo().log("Adding Pipe to Network");

        if (pipe.getNetwork() != null && pipe.getNetwork() != this) {
            pipe.getNetwork().removePipe(pipe);
        }

        pipes.add(pipe);
        pipe.assignNetwork(this);
        rebuildTargets();
    }

    protected void removePipe(LogisticPipeComponent<TContainer> pipe) {

        LOGGER.atInfo().log("Detaching Pipe");

        pipes.remove(pipe);

        if (pipe.getNetwork() == this) {
            pipe.assignNetwork(null);
        }

        rebuildTargets();
    }

    public void rebuildTargets() {

        pullTargets.clear();
        pushTargets.clear();

        for (var pipe : pipes) {
            for (var target : pipe.getNeighbors()) {

                if (target instanceof LogisticPipeComponent<?>) {
                    continue;
                }

                if (pipe.canPullFrom(target)) {
                    pullTargets.add(target);
                }

                if (pipe.canOutputTo(target)) {
                    pushTargets.add(target);
                }
            }
        }

        LOGGER.atInfo().log(
                "Network Rebuilt: %d PULL / %d PUSH Targets",
                pullTargets.size(),
                pushTargets.size()
        );
    }

    public abstract void pullFromTargets();

    public abstract void pushToTargets();
}
