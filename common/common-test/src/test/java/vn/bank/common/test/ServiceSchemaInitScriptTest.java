package vn.bank.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceSchemaInitScriptTest {

    private static final String SCRIPT = """
            #!/bin/bash
            set -Eeuo pipefail

            SERVICE_SCHEMAS=(CUSTOMER_SVC ACCOUNT_SVC  EXTRA_SVC)
            PDB_NAME="${ORACLE_DATABASE:-FREEPDB1}"
            """;

    @Test
    void should_returnSchemasInDeclaredOrder_when_scriptDeclaresServiceSchemas() {
        // when
        var schemas = ServiceSchemaInitScript.parseSchemas(SCRIPT);

        // then
        assertThat(schemas).containsExactly("CUSTOMER_SVC", "ACCOUNT_SVC", "EXTRA_SVC");
    }

    @Test
    void should_readPathAndSchemas_when_loadedFromFile(@TempDir Path dir) throws Exception {
        // given
        var file = Files.writeString(dir.resolve("01_create_service_schemas.sh"), SCRIPT);

        // when
        var script = ServiceSchemaInitScript.fromFile(file);

        // then
        assertThat(script.path()).isEqualTo(file);
        assertThat(script.schemas()).containsExactly("CUSTOMER_SVC", "ACCOUNT_SVC", "EXTRA_SVC");
    }

    @Test
    void should_failFast_when_scriptHasNoServiceSchemasDeclaration() {
        // when
        var thrown = catchThrowable(() -> ServiceSchemaInitScript.parseSchemas("#!/bin/bash\necho hello\n"));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("SERVICE_SCHEMAS");
    }

    @Test
    void should_failFast_when_serviceSchemasIsEmpty() {
        // when
        var thrown = catchThrowable(() -> ServiceSchemaInitScript.parseSchemas("SERVICE_SCHEMAS=( )\n"));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("no schema");
    }

    @Test
    void should_namePasswordVariableLikeInitScript_when_givenSchema() {
        // when
        var variable = ServiceSchemaInitScript.passwordVariableOf("ACCOUNT_SVC");

        // then
        assertThat(variable).isEqualTo("ACCOUNT_SVC_DB_PASSWORD");
    }
}
