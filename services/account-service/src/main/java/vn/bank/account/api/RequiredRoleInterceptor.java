package vn.bank.account.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.common.error.BusinessException;

/**
 * Kiem tra role cua endpoint co {@link RequiresRole} bang header {@code X-Roles}. Gateway da xac thuc JWT, xoa header
 * cung ten do client gui len roi dat lai tu claim {@code realm_access.roles} (BANK-011), nen service tin header nay.
 * Thieu header nghia la khong co role nao: tra 403.
 */
@Component
public class RequiredRoleInterceptor implements HandlerInterceptor {

    static final String ROLES_HEADER = "X-Roles";
    private static final String ROLES_DELIMITER = ",";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        var requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null || hasRole(request.getHeader(ROLES_HEADER), requiresRole.value())) {
            return true;
        }
        throw new BusinessException(AccountErrorCode.ACCESS_DENIED, "Role " + requiresRole.value() + " is required");
    }

    private static boolean hasRole(String rolesHeader, Role role) {
        if (rolesHeader == null) {
            return false;
        }
        return Arrays.stream(rolesHeader.split(ROLES_DELIMITER))
                .map(String::strip)
                .anyMatch(role.name()::equals);
    }
}
