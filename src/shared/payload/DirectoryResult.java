package shared.payload;

import java.util.ArrayList;

public class DirectoryResult implements Payload {
    private ArrayList<UserInfo> result;

    public DirectoryResult(ArrayList<UserInfo> r) {
        this.result = r;
    }

    public ArrayList<UserInfo> getDirectoryResult() { return result; }
}
