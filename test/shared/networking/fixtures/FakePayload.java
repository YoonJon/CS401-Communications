package shared.networking.fixtures;

import shared.payload.Payload;

/**
 * Minimal {@link Payload} used only in networking tests and seed data.
 * Carries a single string value so tests can verify payload identity
 * after a full socket round-trip without depending on any real payload class.
 *
 * Must be public because it is used from {@code shared.networking.NetworkingTest}
 * (different package from this fixtures sub-package).
 */
public class FakePayload implements Payload {

    private static final long serialVersionUID = 1L;

    private final String value;

    public FakePayload(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "FakePayload(" + value + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FakePayload)) return false;
        return value != null ? value.equals(((FakePayload) o).value)
                             : ((FakePayload) o).value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
