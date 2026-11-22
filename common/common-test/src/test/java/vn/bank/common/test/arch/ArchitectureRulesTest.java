package vn.bank.common.test.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Moi rule trong {@link ArchitectureRules} chay tren mot package mau dung ({@code fixture.good}) va it nhat mot
 * package mau vi pham ({@code fixture.bad.*}).
 */
class ArchitectureRulesTest {

    private static final String FIXTURE = "vn.bank.common.test.arch.fixture";
    private static final String GOOD = FIXTURE + ".good";
    private static final String BOOT_ONLY = FIXTURE + ".bootonly";

    static List<Function<String, ArchRule>> allRules() {
        return List.of(
                ArchitectureRules::layerDependencies,
                ArchitectureRules::apiShouldNotDependOnInfrastructure,
                ArchitectureRules::domainShouldNotDependOnSpring,
                ArchitectureRules::transactionalOnlyInApplication,
                ArchitectureRules::noFloatingPointFields,
                ArchitectureRules::entitiesShouldNotHaveSetters);
    }

    @ParameterizedTest
    @MethodSource("allRules")
    void should_passRule_when_serviceFollowsConvention(Function<String, ArchRule> rule) {
        // when
        var result = evaluate(rule.apply(GOOD), GOOD);

        // then
        assertThat(result.hasViolation())
                .as(result.getFailureReport().toString())
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("allRules")
    void should_passRule_when_serviceHasNoLayerPackagesYet(Function<String, ArchRule> rule) {
        // when
        var result = evaluate(rule.apply(BOOT_ONLY), BOOT_ONLY);

        // then
        assertThat(result.hasViolation())
                .as(result.getFailureReport().toString())
                .isFalse();
    }

    @Test
    void should_reportLayerViolation_when_domainDependsOnApplication() {
        assertViolation(ArchitectureRules::layerDependencies, "domaintoapplication", "BadAccount");
    }

    @Test
    void should_reportLayerViolation_when_infrastructureOutsideMessagingDependsOnApplication() {
        assertViolation(ArchitectureRules::layerDependencies, "infratoapplication", "BadRepository");
    }

    @Test
    void should_reportLayerViolation_when_applicationDependsOnApi() {
        assertViolation(ArchitectureRules::layerDependencies, "applicationtoapi", "BadService");
    }

    @Test
    void should_reportViolation_when_apiDependsOnInfrastructure() {
        assertViolation(ArchitectureRules::apiShouldNotDependOnInfrastructure, "apitoinfrastructure", "BadController");
    }

    @Test
    void should_reportViolation_when_domainDependsOnSpring() {
        assertViolation(ArchitectureRules::domainShouldNotDependOnSpring, "domaintospring", "BadDomainService");
    }

    @Test
    void should_reportViolation_when_transactionalOnClassOutsideApplication() {
        assertViolation(ArchitectureRules::transactionalOnlyInApplication, "transactionalclass", "BadController");
    }

    @Test
    void should_reportViolation_when_transactionalOnMethodOutsideApplication() {
        assertViolation(ArchitectureRules::transactionalOnlyInApplication, "transactionalmethod", "BadRepository.save");
    }

    @Test
    void should_reportEveryField_when_fieldIsDoubleFloatOrWrapper() {
        // given
        var basePackage = FIXTURE + ".bad.floatingpoint";

        // when
        var result = evaluate(ArchitectureRules.noFloatingPointFields(basePackage), basePackage);

        // then
        assertThat(result.getFailureReport().getDetails())
                .hasSize(4)
                .anyMatch(detail -> detail.contains("BadFee.amount"))
                .anyMatch(detail -> detail.contains("BadFee.rate"))
                .anyMatch(detail -> detail.contains("BadFee.discount"))
                .anyMatch(detail -> detail.contains("BadFee.tax"));
    }

    @Test
    void should_reportViolation_when_entityHasPublicSetter() {
        assertViolation(ArchitectureRules::entitiesShouldNotHaveSetters, "entitysetter", "BadAccount.setBalance");
    }

    private static void assertViolation(Function<String, ArchRule> rule, String badFixture, String offender) {
        // given
        var basePackage = FIXTURE + ".bad." + badFixture;

        // when
        var result = evaluate(rule.apply(basePackage), basePackage);

        // then
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains(offender));
    }

    private static EvaluationResult evaluate(ArchRule rule, String basePackage) {
        return rule.evaluate(new ClassFileImporter().importPackages(basePackage));
    }
}
