package shared.payload;

public class DirectoryQuery implements Payload {
    private String query;

    public DirectoryQuery(String q) {
        this.query = q;
    }

    public String getQuery() { return query; }
}
