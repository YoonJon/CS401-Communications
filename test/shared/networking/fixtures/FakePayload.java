package shared.networking.fixtures;

import shared.payload.RequestPayload;
import shared.payload.ResponsePayload;

/**
 * Minimal test payload used only in networking tests and seed data.
 * Implements both {@link RequestPayload} and {@link ResponsePayload} so it can
 * be passed to both {@code Request} and {@code Response} constructors without
 * requiring separate stub classes.
 *
 * Must be public because it is used from {@code shared.networking.NetworkingTest}
 * (different package from this fixtures sub-package).
 */
public class FakePayload implements RequestPayload, ResponsePayload {

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
