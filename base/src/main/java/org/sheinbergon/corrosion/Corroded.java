package org.sheinbergon.corrosion;

import lombok.Getter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

@Accessors(fluent = true)
public class Corroded extends Thread {

    @FunctionalInterface
    private interface Initializer {
        void invoke();
    }

    @Getter
    private volatile Object nativeId = null;

    @Nullable
    private Initializer initializer = null;

    public Corroded(final @Nonnull Runnable target) {
        super(target);
    }

    public Corroded(final @Nonnull Runnable target, final long mask) {
        super(target);
        initializer = () -> affinity(mask);
    }

    public Corroded(final @Nonnull Runnable target, final @Nonnull String mask) {
        super(target);
        initializer = () -> affinity(mask);
    }

    protected Corroded(final long mask) {
        super();
        initializer = () -> affinity(mask);
    }

    protected Corroded(final String mask) {
        super();
        initializer = () -> affinity(mask);
    }

    public final CoreSet affinity() {
        return Corrosion.get(this);
    }

    public final void affinity(final @Nonnull String mask) {
        Corrosion.set(mask, this);
    }

    public final void affinity(final long mask) {
        Corrosion.set(mask, this);
    }

    protected final void setup() {
        nativeId = Corrosion.self();
        Objects.requireNonNull(nativeId);
        if (initializer != null) initializer.invoke();
    }

    @Override
    public void run() {
        setup();
        super.run();
    }
}