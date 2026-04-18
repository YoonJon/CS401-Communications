package shared.payload;

public class DirectoryQuery implements RequestPayload {
    private final String query;

    public DirectoryQuery(String q) {
        this.query = q;
    }

    public String getQuery() { return query; }
}
