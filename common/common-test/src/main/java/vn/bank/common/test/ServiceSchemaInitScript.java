package vn.bank.common.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Script tao schema cua service ({@code infra/oracle/init/01_create_service_schemas.sh}), dung chung cho
 * docker-compose va container Oracle cua test. Script la nguon duy nhat cua danh sach schema: test doc danh sach tu
 * mang {@code SERVICE_SCHEMAS} trong script de dat bien {@code <SCHEMA>_DB_PASSWORD} ma script yeu cau.
 */
final class ServiceSchemaInitScript {

    /** System property chua duong dan goc repo, failsafe truyen vao tu {@code maven.multiModuleProjectDirectory}. */
    static final String REPO_ROOT_PROPERTY = "bank.test.repo-root";

    private static final String SCRIPT_PATH_IN_REPO = "infra/oracle/init/01_create_service_schemas.sh";
    private static final Pattern SCHEMAS_DECLARATION =
            Pattern.compile("^SERVICE_SCHEMAS=\\(([^)]*)\\)", Pattern.MULTILINE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Path path;
    private final List<String> schemas;

    private ServiceSchemaInitScript(Path path, List<String> schemas) {
        this.path = path;
        this.schemas = schemas;
    }

    /** Doc script trong repo co goc lay tu system property {@link #REPO_ROOT_PROPERTY}. */
    static ServiceSchemaInitScript fromRepoRoot() {
        var repoRoot = System.getProperty(REPO_ROOT_PROPERTY);
        if (repoRoot == null || repoRoot.isBlank()) {
            throw new IllegalStateException("System property " + REPO_ROOT_PROPERTY
                    + " is not set. Run integration tests via Maven failsafe or pass -D" + REPO_ROOT_PROPERTY
                    + "=<repo root>");
        }
        return fromFile(Path.of(repoRoot).resolve(SCRIPT_PATH_IN_REPO));
    }

    static ServiceSchemaInitScript fromFile(Path path) {
        try {
            return new ServiceSchemaInitScript(path, parseSchemas(Files.readString(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Oracle init script " + path, e);
        }
    }

    static List<String> parseSchemas(String scriptContent) {
        var matcher = SCHEMAS_DECLARATION.matcher(scriptContent);
        if (!matcher.find()) {
            throw new IllegalStateException("Oracle init script has no SERVICE_SCHEMAS=(...) declaration");
        }
        var schemas = Arrays.stream(WHITESPACE.split(matcher.group(1).strip()))
                .filter(schema -> !schema.isEmpty())
                .toList();
        if (schemas.isEmpty()) {
            throw new IllegalStateException("Oracle init script declares no schema in SERVICE_SCHEMAS");
        }
        return schemas;
    }

    /** Ten bien moi truong chua mat khau cua {@code schema}, theo quy uoc cua script. */
    static String passwordVariableOf(String schema) {
        return schema + "_DB_PASSWORD";
    }

    Path path() {
        return path;
    }

    List<String> schemas() {
        return schemas;
    }
}
