package at.rasebdon.hytech.core.components;

public interface ContainerHolder<TContainer> {
    TContainer getContainer();

    boolean isAvailable();
}
