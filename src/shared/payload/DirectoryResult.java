package shared.payload;

import java.util.ArrayList;

public class DirectoryResult implements ResponsePayload {
    private final ArrayList<UserInfo> result;

    public DirectoryResult(ArrayList<UserInfo> r) {
        this.result = r;
    }

    public ArrayList<UserInfo> getDirectoryResult() { return result; }
}
