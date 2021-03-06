package org.sheinbergon.needle;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sheinbergon.needle.util.AffinityDescriptorException;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Accessors(fluent = true)
public class AffinityDescriptor {

    static final int AVAILABLE_CORES = Runtime.getRuntime().availableProcessors();
    static final int MASK_UPPER_BOUND = (int) Math.pow(2, AVAILABLE_CORES);
    static final String SPECIFICATION_DELIMITER = ",";
    static final String RANGE_DELIMITER = "-";

    static final AffinityDescriptor UNSUPPORTED = new AffinityDescriptor() {
        @Override
        public long mask() {
            return NumberUtils.LONG_MINUS_ONE;
        }

        @Override
        public String toString() {
            return null;
        }
    };

    private static final Pattern RANGE = Pattern.compile("^\\s*(\\d{1,3})\\s*-\\s*(\\d{1,3})\\s*$");
    private static final Pattern SINGLE = Pattern.compile("^\\s*(\\d{1,3})\\s*$");

    @Nonnull
    private final Set<Specification> specifications;

    @Getter(lazy = true)
    private final long mask = computeMask();

    private AffinityDescriptor(Specification... specifications) {
        this.specifications = Set.of(specifications);
    }

    static AffinityDescriptor process() {
        return AffinityResolver.instance.process();
    }

    public static AffinityDescriptor from(final long mask) {
        val specifications = new HashSet<Specification>();
        Integer start = null, end = null;
        for (var core = 0; core < Long.SIZE; core++) {
            if (((1L << core) & mask) > 0) {
                if (start == null)
                    start = core;
                end = core;
            } else {
                if (start != null && end != null) {
                    specifications.add(Specification.from(start, end));
                    end = start = null;
                }
            }
        }
        return from(specifications);
    }

    public static AffinityDescriptor from(final @Nonnull String mask) {
        val specifications = Arrays.stream(StringUtils.split(mask, SPECIFICATION_DELIMITER))
                .filter(Predicate.not(String::isBlank))
                .map(AffinityDescriptor::specificationFrom)
                .collect(Collectors.toSet());
        return from(specifications);
    }

    private static AffinityDescriptor from(final @Nonnull Set<Specification> specifications) {
        if (specifications.isEmpty()) {
            return process();
        } else {
            val descriptor = new AffinityDescriptor(specifications);
            validate(descriptor.mask());
            return descriptor;
        }
    }

    private static Specification specificationFrom(final @Nonnull String specification) {
        int start, end;
        Matcher matcher;
        if ((matcher = RANGE.matcher(specification)).matches()) {
            start = Integer.parseInt(matcher.group(1));
            end = Integer.parseInt(matcher.group(2));
        } else if ((matcher = SINGLE.matcher(specification)).matches()) {
            start = end = Integer.parseInt(matcher.group());
        } else {
            throw new AffinityDescriptorException(String.format("Illegal specification - '%s'", specification));
        }
        return Specification.from(start, end);
    }

    private static void validate(final long mask) {
        if (mask <= 0 || mask > AffinityDescriptor.MASK_UPPER_BOUND) {
            throw new AffinityDescriptorException(
                    String.format("Mask %d is out of bounds, only %d cores are available",
                            mask, AffinityDescriptor.AVAILABLE_CORES));
        }
    }

    @Override
    public String toString() {
        return specifications.stream()
                .map(Specification::toString)
                .sorted()
                .collect(Collectors.joining(SPECIFICATION_DELIMITER));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mask());
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else if (!(other instanceof AffinityDescriptor)) {
            return false;
        } else {
            return ((AffinityDescriptor) other).mask() == this.mask();
        }
    }

    private long computeMask() {
        return Optional.ofNullable(specifications).map(Set::stream)
                .map(stream -> stream.map(Specification::mask))
                .flatMap(stream -> stream.reduce((m1, m2) -> m1 | m2))
                .orElse(NumberUtils.LONG_ZERO);
    }

    private interface Specification {

        private static Specification from(int start, int end) {
            val diff = end - start;
            if (diff > 0) {
                return new Specification.Range(start, end);
            } else if (diff == 0) {
                return new Specification.Single(end);
            } else {
                throw new AffinityDescriptorException(String.format("Invalid core range, starts at %d, ends at %d", start, end));
            }
        }

        long mask();

        @RequiredArgsConstructor
        @Accessors(fluent = true)
        class Single implements Specification {
            private final int core;

            @Override
            public long mask() {
                return 1 << core;
            }

            @Override
            public String toString() {
                return Integer.toString(core);
            }
        }

        @RequiredArgsConstructor
        @Accessors(fluent = true)
        class Range implements Specification {
            private final int start;
            private final int end;

            @Getter(lazy = true)
            private final long mask = computeMask();

            private long computeMask() {
                return IntStream.rangeClosed(start, end)
                        .map(core -> 1 << core)
                        .reduce((c1, c2) -> c1 | c2)
                        .orElseThrow(() -> new IllegalArgumentException("Illegal range specified"));
            }

            @Override
            public String toString() {
                return String.format("%d%s%d", start, RANGE_DELIMITER, end);
            }
        }
    }
}
