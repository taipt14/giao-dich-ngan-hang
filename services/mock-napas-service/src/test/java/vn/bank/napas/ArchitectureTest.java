package vn.bank.napas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import vn.bank.common.test.arch.ArchitectureRules;

/** Ep quy tac kien truc dung chung (muc 2, 4, 7, 8 convention-code.md) cho code main cua service. */
@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "vn.bank.napas";

    @ArchTest
    static final ArchRule layer_dependencies = ArchitectureRules.layerDependencies(BASE_PACKAGE);

    @ArchTest
    static final ArchRule api_should_not_depend_on_infrastructure =
            ArchitectureRules.apiShouldNotDependOnInfrastructure(BASE_PACKAGE);

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
            ArchitectureRules.domainShouldNotDependOnSpring(BASE_PACKAGE);

    @ArchTest
    static final ArchRule transactional_only_in_application =
            ArchitectureRules.transactionalOnlyInApplication(BASE_PACKAGE);

    @ArchTest
    static final ArchRule no_floating_point_fields = ArchitectureRules.noFloatingPointFields(BASE_PACKAGE);

    @ArchTest
    static final ArchRule entities_should_not_have_setters =
            ArchitectureRules.entitiesShouldNotHaveSetters(BASE_PACKAGE);
}
