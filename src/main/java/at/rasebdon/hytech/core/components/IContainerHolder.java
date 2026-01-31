package at.rasebdon.hytech.core.components;

public interface IContainerHolder<TContainer> {
    TContainer getContainer();

    boolean isAvailable();
}
