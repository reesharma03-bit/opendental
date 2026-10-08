package com.clinic.opendental.service.Impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Open Dental's user rules (apiuserods.html). */
class UserodRulesTest {

    @Test
    void aUserNeedsANameAGroupAndAStrongPassword() {
        assertThatCode(() -> UserodRules.checkCreate(Map.of("UserName", "Sally", "UserGroupNum", 2, "Password", "My1password")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> UserodRules.checkCreate(Map.of()))
                .hasMessageContaining("UserName is required").hasMessageContaining("UserGroupNum").hasMessageContaining("Password is required");
        assertThatThrownBy(() -> UserodRules.checkCreate(Map.of("UserName", "Sally ", "UserGroupNum", 2, "Password", "My1password")))
                .hasMessageContaining("can't end with a space");
        for (String weak : List.of("Short1a", "alllower1", "ALLUPPER1", "NoDigitsHere")) {
            assertThatThrownBy(() -> UserodRules.checkCreate(Map.of("UserName", "Sally", "UserGroupNum", 2, "Password", weak)))
                    .as(weak).hasMessageContaining("at least 8 characters");
        }
        assertThatThrownBy(() -> UserodRules.checkCreate(Map.of("UserName", "S", "UserGroupNum", 2, "Password", "My1password", "ProviderNum", 3)))
                .hasMessageContaining("set it afterwards");
    }

    @Test
    void anUpdateOnlyTouchesWhatOpenDentalAllows() {
        assertThatCode(() -> UserodRules.checkUpdate(Map.of("userGroupNums", List.of(2, 4, 8), "ProviderNum", 2, "IsHidden", "false")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> UserodRules.checkUpdate(Map.of("Password", "New1password")))
                .hasMessageContaining("change it in Open Dental");
        assertThatThrownBy(() -> UserodRules.checkUpdate(Map.of("userGroupNums", List.of())))
                .hasMessageContaining("at least one group");
        assertThatThrownBy(() -> UserodRules.checkUpdate(Map.of("IsHidden", "yes")))
                .hasMessageContaining("\"true\" or \"false\"");
    }
}
