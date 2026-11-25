package com.heuristiq.identity.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.enums.UamEnums;

class UserDirectoryControllerTest {

    private final UamUserServiceUseCase users = mock(UamUserServiceUseCase.class);
    private final UserDirectoryController controller = new UserDirectoryController(users);

    private static UamUser user(String id, String userName, String displayName, String email) {
        UamUser user = new UamUser();
        user.setId(id);
        user.setUserName(userName);
        user.setDisplayName(displayName);
        user.setEmail(email);
        return user;
    }

    private void found(UamUser... found) {
        List<UamUser> list = List.of(found);
        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(
                new PageResult<>(list, list.size(), 1, 50, 0, list.size(), true, true, list.isEmpty())));
    }

    @SuppressWarnings("unchecked")
    private static List<UserDirectoryController.DirectoryUser> people(CommonResponseObject response) {
        return (List<UserDirectoryController.DirectoryUser>) response.getResponseObject();
    }

    @Test
    void returnsOnlyIdNameAndEmail() {
        found(user("u1", "asha", "Asha Rao", "asha@x.com"));

        List<UserDirectoryController.DirectoryUser> result = people(controller.list("MATRIMONY_RM", null, 50));

        assertThat(result).containsExactly(new UserDirectoryController.DirectoryUser("u1", "Asha Rao", "asha@x.com"));
    }

    @Test
    void fallsBackToTheUserNameThenEmailWhenThereIsNoDisplayName() {
        found(user("u1", "asha", null, "asha@x.com"), user("u2", " ", null, "ravi@x.com"));

        assertThat(people(controller.list("MATRIMONY_RM", null, 50))).extracting("displayName")
                .containsExactly("asha", "ravi@x.com");
    }

    @Test
    void asksForActiveUsersWithTheRoleAndTrimsTheSearch() {
        found();

        controller.list(" MATRIMONY_RM ", "  asha ", 50);

        ArgumentCaptor<UamUserServiceUseCase.SearchInput> input = ArgumentCaptor.forClass(UamUserServiceUseCase.SearchInput.class);
        verify(users).search(input.capture());
        assertThat(input.getValue().roleCode()).isEqualTo("MATRIMONY_RM");
        assertThat(input.getValue().status()).isEqualTo(UamEnums.UserStatus.ACTIVE);
        assertThat(input.getValue().search()).isEqualTo("asha");
        assertThat(input.getValue().page()).isZero();
    }

    @Test
    void aBlankSearchIsNoSearch() {
        found();

        controller.list("MATRIMONY_RM", "   ", 50);

        ArgumentCaptor<UamUserServiceUseCase.SearchInput> input = ArgumentCaptor.forClass(UamUserServiceUseCase.SearchInput.class);
        verify(users).search(input.capture());
        assertThat(input.getValue().search()).isNull();
    }

    @Test
    void theResultSizeIsCappedAndNeverBelowOne() {
        found();
        ArgumentCaptor<UamUserServiceUseCase.SearchInput> input = ArgumentCaptor.forClass(UamUserServiceUseCase.SearchInput.class);

        controller.list("R", null, 100_000);
        controller.list("R", null, -5);

        verify(users, org.mockito.Mockito.times(2)).search(input.capture());
        assertThat(input.getAllValues()).extracting(UamUserServiceUseCase.SearchInput::size)
                .containsExactly(UamUserConstants.DIRECTORY_MAX_RESULTS, 1);
    }
}
