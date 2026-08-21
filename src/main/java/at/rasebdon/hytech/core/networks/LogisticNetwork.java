package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.*;

public abstract class LogisticNetwork<TContainer> extends ContainerHolder<TContainer> {

    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final Set<LogisticPipeComponent<TContainer>> pipes = new HashSet<>();
    protected final List<ContainerHolder<TContainer>> pullTargets = new ArrayList<>();
    protected final List<ContainerHolder<TContainer>> pushTargets = new ArrayList<>();

    protected LogisticNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        super();
        setPipes(initialPipes);
    }

    public Set<LogisticPipeComponent<TContainer>> getPipes() {
        return Set.copyOf(pipes);
    }

    protected void setPipes(Set<LogisticPipeComponent<TContainer>> newPipes) {

        LOGGER.atFine().log("Setting Network with %d Pipes", newPipes.size());

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
        onPipesChanged();
    }

    /// Called after any change to the pipe set, once targets have been rebuilt.
    ///
    /// Every subclass overrode `setPipes`/`addPipe`/`removePipe` purely to recompute its
    /// aggregate afterwards. One hook says the same thing and cannot be half-implemented.
    protected void onPipesChanged() {
    }

    public List<ContainerHolder<TContainer>> getPullTargets() {
        return Collections.unmodifiableList(pullTargets);
    }

    public List<ContainerHolder<TContainer>> getPushTargets() {
        return Collections.unmodifiableList(pushTargets);
    }

    protected void addPipe(LogisticPipeComponent<TContainer> pipe) {

        LOGGER.atFine().log("Adding Pipe to Network");

        if (pipe.getNetwork() != null && pipe.getNetwork() != this) {
            pipe.getNetwork().removePipe(pipe);
        }

        pipes.add(pipe);
        pipe.assignNetwork(this);
        rebuildTargets();
        onPipesChanged();
    }

    protected void removePipe(LogisticPipeComponent<TContainer> pipe) {

        LOGGER.atFine().log("Detaching Pipe");

        pipes.remove(pipe);

        if (pipe.getNetwork() == this) {
            pipe.assignNetwork(null);
        }

        rebuildTargets();
        onPipesChanged();
    }

    public void rebuildTargets() {

        pullTargets.clear();
        pushTargets.clear();

        for (var pipe : pipes) {
            for (var target : pipe.getNeighbors()) {

                if (target.getHolder() instanceof LogisticPipeComponent<?>) {
                    continue;
                }

                if (pipe.canPullFrom(target)) {
                    pullTargets.add(target.getHolder());
                }

                if (pipe.canOutputTo(target)) {
                    pushTargets.add(target.getHolder());
                }
            }
        }

        LOGGER.atFine().log(
                "Network Rebuilt: %d PULL / %d PUSH Targets",
                pullTargets.size(),
                pushTargets.size()
        );
    }
}
