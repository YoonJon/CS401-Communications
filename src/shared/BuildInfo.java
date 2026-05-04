package shared;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Embedded {@link #VERSION} and {@link #GIT_REVISION} are updated by {@code scripts/write-build-info.sh}
 * (fallback when Git is unavailable at runtime). {@link #formatVersionForLog()} prefers live {@code git}
 * from the process working directory when you run from a checkout.
 */
public final class BuildInfo {
    private static final int GIT_WAIT_SEC = 5;

    private BuildInfo() {}

    public static final int VERSION = 185;
    public static final String GIT_REVISION = "030a08a-dirty";

    /** Same shape as the embedded log segment: {@code "<count> (<hash>[-dirty])"}. */
    public static String formatVersionForLog() {
        String rev = gitDescribeAlwaysDirtyOrNull();
        Integer count = gitRevListCountOrNull();
        int versionNum = count != null ? count : VERSION;
        String revStr = rev != null ? rev : GIT_REVISION;
        return versionNum + " (" + revStr + ")";
    }

    private static String gitDescribeAlwaysDirtyOrNull() {
        return runGitStdoutOrNull("git", "describe", "--always", "--dirty");
    }

    private static Integer gitRevListCountOrNull() {
        String out = runGitStdoutOrNull("git", "rev-list", "--count", "HEAD");
        if (out == null) {
            return null;
        }
        try {
            return Integer.parseInt(out.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String runGitStdoutOrNull(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(GIT_WAIT_SEC, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }
}
