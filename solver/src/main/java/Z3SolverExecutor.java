import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

/**
 * A smt2 solver implementation using Z3 in a Docker container.
 */
public class Z3SolverExecutor implements AutoCloseable {

    public static final String Z3_DOCKER_IMAGE = "ghcr.io/z3prover/z3:ubuntu-20.04-bare-z3-sha-ba8d8f0";

    // The default entrypoint runs directly z3, we need to keep it running
    public static final String ENTRYPOINT = "while :; do sleep 1000 ; done";

    private final String containerPath = "/smt2-resources/";
    private final GenericContainer<?>  z3Prover;

    /**
     * The current implementation of the Z3 solver reads content either from STDIN or from a file.
     * In this case, it is reading from a file each time it needs to solve a problem.
     * As the file generation is dynamical, the Docker container needs to have access to the file system.
     * So, the Docker volume is linked to the file system folder.
     * Then, the result is returned in STDOUT.
     * @param resourcesFolder the name of the folder in the file system that will be linked to the Docker volume
     */
    public Z3SolverExecutor(String resourcesFolder) {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder -> builder
                        .from(Z3_DOCKER_IMAGE)
                        .entryPoint(ENTRYPOINT)
                        .build());

        z3Prover = new GenericContainer<>(image)
                        .withFileSystemBind(resourcesFolder, containerPath, BindMode.READ_WRITE);

        z3Prover.start();
    }

    /**
     * Stops the Z3 Docker container.
     */
    @Override
    public void close() {
        z3Prover.stop();
    }

    /**
     * Reads from a file in the container 'filename' the smt2 problem, and runs z3 with it.
     * Returns the result as string.
     * The file must be in the containerPath defined in the constructor.
     * @param fileName the name of the file to read (only the file name, as the directory is already linked)
     * @return the result of the Z3 solver with the obtained model as string
     */
    public String solveFromFile(String fileName) {
        try {
            Container.ExecResult result = z3Prover.execInContainer("z3", containerPath + fileName);
            String stdout = result.getStdout();
            if (stdout == null || stdout.isEmpty()) {
                String stderr = result.getStderr();
                throw new RuntimeException(stderr);
            }
            return stdout;
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
