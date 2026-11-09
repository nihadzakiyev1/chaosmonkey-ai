import java.nio.file.Files;
import java.nio.file.Path;

/** IDE entry point: starts the complete desktop application, including its worker. */
public class qa {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("scripts/start-local.ps1"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("Run from the QA app project directory.");
        int code = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", root.resolve("scripts/start-local.ps1").toString())
            .directory(root.toFile()).inheritIO().start().waitFor();
        if (code != 0) throw new IllegalStateException("Startup failed. See data/local logs above.");
    }
}
