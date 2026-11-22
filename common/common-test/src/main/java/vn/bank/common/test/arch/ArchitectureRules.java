package vn.bank.common.test.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * Rule ArchUnit dung chung cho moi service, ep cac quy tac muc 2, 4, 7, 8 cua {@code docs/convention-code.md}.
 *
 * <p>Moi service co mot {@code ArchitectureTest} khai bao {@code @ArchTest} goi cac method duoi day voi package goc
 * cua service (vi du {@code vn.bank.account}). Moi rule deu {@code allowEmptyShould(true)}: service chua co package
 * {@code api}/{@code domain}... thi rule khong co gi de kiem va van pass.
 */
public final class ArchitectureRules {

    private static final String API = "api";
    private static final String APPLICATION = "application";
    private static final String DOMAIN = "domain";
    private static final String INFRASTRUCTURE = "infrastructure";
    private static final String MESSAGING = INFRASTRUCTURE + ".messaging";
    private static final String JOB_SUFFIX = "Job";

    private static final String SPRING_PACKAGE = "org.springframework..";
    private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";
    private static final String JPA_ENTITY = "jakarta.persistence.Entity";
    private static final String SETTER_PREFIX = "set";

    private ArchitectureRules() {}

    /**
     * Phu thuoc giua cac tang dung bang muc 2. Ngoai le duy nhat cho chieu infrastructure -> application: class trong
     * {@code infrastructure.messaging} (consumer) va class ten {@code *Job}.
     */
    public static ArchRule layerDependencies(String basePackage) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer(API)
                .definedBy(inLayer(basePackage, API))
                .layer(APPLICATION)
                .definedBy(inLayer(basePackage, APPLICATION))
                .layer(DOMAIN)
                .definedBy(inLayer(basePackage, DOMAIN))
                .layer(INFRASTRUCTURE)
                .definedBy(inLayer(basePackage, INFRASTRUCTURE))
                .whereLayer(API)
                .mayOnlyAccessLayers(APPLICATION, DOMAIN)
                .whereLayer(APPLICATION)
                .mayOnlyAccessLayers(DOMAIN, INFRASTRUCTURE)
                .whereLayer(DOMAIN)
                .mayNotAccessAnyLayer()
                .whereLayer(INFRASTRUCTURE)
                .mayOnlyAccessLayers(DOMAIN)
                .ignoreDependency(consumerOrJob(basePackage), resideInAPackage(inLayer(basePackage, APPLICATION)))
                .allowEmptyShould(true);
    }

    /** Controller (tang api) khong goi thang repository hay bat ky class nao cua infrastructure. */
    public static ArchRule apiShouldNotDependOnInfrastructure(String basePackage) {
        return noClasses()
                .that()
                .resideInAPackage(inLayer(basePackage, API))
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true);
    }

    /** Domain khong phu thuoc Spring. Annotation {@code jakarta.persistence} van duoc phep. */
    public static ArchRule domainShouldNotDependOnSpring(String basePackage) {
        return noClasses()
                .that()
                .resideInAPackage(inLayer(basePackage, DOMAIN))
                .should()
                .dependOnClassesThat()
                .resideInAPackage(SPRING_PACKAGE)
                .allowEmptyShould(true);
    }

    /** {@code @Transactional} (Spring hoac Jakarta) chi dat tren class/method thuoc tang application. */
    public static ArchRule transactionalOnlyInApplication(String basePackage) {
        var applicationPackage = inLayer(basePackage, APPLICATION);
        var onClass = classes()
                .that(transactional())
                .should()
                .resideInAPackage(applicationPackage)
                .allowEmptyShould(true);
        var onMethod = methods()
                .that(transactional())
                .should()
                .beDeclaredInClassesThat()
                .resideInAPackage(applicationPackage)
                .allowEmptyShould(true);
        return CompositeArchRule.of(onClass).and(onMethod).allowEmptyShould(true);
    }

    /**
     * Khong field nao kieu {@code double}, {@code float}, {@code Double}, {@code Float}: khong phan biet duoc field nao
     * la tien, ma project khong co nhu cau so thuc nhi phan.
     */
    public static ArchRule noFloatingPointFields(String basePackage) {
        return noFields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(basePackage + "..")
                .should()
                .haveRawType(double.class)
                .orShould()
                .haveRawType(float.class)
                .orShould()
                .haveRawType(Double.class)
                .orShould()
                .haveRawType(Float.class)
                .allowEmptyShould(true);
    }

    /**
     * Entity khong co method public ten bat dau bang {@code set}. Kiem setter thay vi annotation vi {@code @Data},
     * {@code @Setter} cua Lombok co retention SOURCE, khong con trong bytecode.
     */
    public static ArchRule entitiesShouldNotHaveSetters(String basePackage) {
        return noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(basePackage + "..")
                .and()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(JPA_ENTITY)
                .and()
                .arePublic()
                .should()
                .haveNameStartingWith(SETTER_PREFIX)
                .allowEmptyShould(true);
    }

    private static String inLayer(String basePackage, String layer) {
        return basePackage + "." + layer + "..";
    }

    private static DescribedPredicate<JavaClass> consumerOrJob(String basePackage) {
        return resideInAPackage(inLayer(basePackage, MESSAGING))
                .or(simpleNameEndingWith(JOB_SUFFIX))
                .as("consumer in %s or *%s", MESSAGING, JOB_SUFFIX);
    }

    private static DescribedPredicate<CanBeAnnotated> transactional() {
        return annotatedWith(SPRING_TRANSACTIONAL)
                .or(annotatedWith(JAKARTA_TRANSACTIONAL))
                .as("annotated with @Transactional");
    }
}
