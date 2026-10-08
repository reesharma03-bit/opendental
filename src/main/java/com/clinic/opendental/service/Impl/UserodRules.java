package com.clinic.opendental.service.Impl;

import com.clinic.opendental.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Open Dental's rules for its own users ("userods", https://www.opendental.com/site/apiuserods.html),
 * checked before anything is sent.
 *
 * <ul>
 *   <li><b>Create</b> needs UserName (unique, no trailing space), UserGroupNum and a strong Password:
 *       at least 8 characters with a number, an upper-case and a lower-case letter.</li>
 *   <li><b>Update</b> may change userGroupNums, EmployeeNum, ProviderNum, ClinicNum, IsHidden and
 *       IsPasswordResetRequired. The user name and password can't be changed through the API.</li>
 *   <li>There is no delete; hide a user instead.</li>
 * </ul>
 */
public final class UserodRules {

    static final Set<String> CREATE_FIELDS = Set.of("UserName", "UserGroupNum", "Password", "IsPasswordResetRequired");
    static final Set<String> UPDATABLE = Set.of("userGroupNums", "EmployeeNum", "ProviderNum", "ClinicNum", "IsHidden", "IsPasswordResetRequired");

    private UserodRules() {
    }

    public static void checkCreate(Map<String, Object> body) {
        List<String> problems = new ArrayList<>();
        for (String field : body.keySet()) {
            if (CREATE_FIELDS.stream().noneMatch(f -> f.equalsIgnoreCase(field))) {
                problems.add(field + " can't be set when creating a user; set it afterwards.");
            }
        }
        String userName = raw(body, "UserName");
        if (userName.isBlank()) problems.add("UserName is required.");
        else if (!userName.equals(userName.stripTrailing())) problems.add("UserName can't end with a space.");
        if (text(body, "UserGroupNum").isEmpty()) problems.add("UserGroupNum (the user's group) is required.");
        else number(body, "UserGroupNum", problems);
        String password = raw(body, "Password");
        if (password.isEmpty()) problems.add("Password is required.");
        else if (password.length() < 8 || !password.matches(".*\\d.*") || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")) {
            problems.add("Password must be at least 8 characters with a number, an upper-case and a lower-case letter.");
        }
        bool(body, "IsPasswordResetRequired", problems);
        fail(problems);
    }

    public static void checkUpdate(Map<String, Object> changes) {
        List<String> problems = new ArrayList<>();
        for (String field : changes.keySet()) {
            if (UPDATABLE.stream().noneMatch(f -> f.equalsIgnoreCase(field))) {
                problems.add(field.equalsIgnoreCase("UserName") || field.equalsIgnoreCase("Password")
                        ? field + " can't be changed through Open Dental's API; change it in Open Dental."
                        : field + " can't be changed. Open Dental allows: " + String.join(", ", UPDATABLE.stream().sorted().toList()) + ".");
            }
        }
        Object groups = value(changes, "userGroupNums");
        if (groups != null) {
            if (!(groups instanceof Collection<?> list) || list.isEmpty()) {
                problems.add("userGroupNums must be a list with at least one group, e.g. [2, 4].");
            } else if (list.stream().anyMatch(g -> g == null || !String.valueOf(g).matches("\\d+"))) {
                problems.add("userGroupNums must contain group numbers.");
            }
        }
        for (String field : List.of("EmployeeNum", "ProviderNum", "ClinicNum")) number(changes, field, problems);
        bool(changes, "IsHidden", problems);
        bool(changes, "IsPasswordResetRequired", problems);
        fail(problems);
    }

    /** A user record as we may keep it: never with a password (Open Dental echoes it on create). */
    public static boolean isSecret(String field) {
        return field.equalsIgnoreCase("Password");
    }

    private static void number(Map<String, Object> body, String field, List<String> problems) {
        String text = text(body, field);
        if (!text.isEmpty() && !text.matches("\\d+")) problems.add(field + " must be a number.");
    }

    private static void bool(Map<String, Object> body, String field, List<String> problems) {
        String text = text(body, field);
        if (!text.isEmpty() && !text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
            problems.add(field + " must be \"true\" or \"false\".");
        }
    }

    private static Object value(Map<String, Object> body, String field) {
        return body.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(field)).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static String raw(Map<String, Object> body, String field) {
        Object value = value(body, field);
        return value == null ? "" : String.valueOf(value);
    }

    private static String text(Map<String, Object> body, String field) {
        return raw(body, field).trim();
    }

    private static void fail(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
    }
}
