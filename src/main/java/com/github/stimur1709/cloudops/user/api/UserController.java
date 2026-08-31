package com.github.stimur1709.cloudops.user.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable long id, Authentication authentication) {
        return UserResponse.from(userService.getOwn(id, CurrentUser.id(authentication)));
    }

    @PostMapping("/search")
    public SearchResponse<UserResponse> search(
            @Valid @RequestBody SearchRequest request, Authentication authentication) {
        return SearchResponse.from(
                userService.search(request.toQuery(), CurrentUser.id(authentication)), UserResponse::from);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable long id, @Valid @RequestBody UpdateUserRequest request, Authentication authentication) {
        return UserResponse.from(
                userService.update(id, request.email(), request.displayName(), CurrentUser.id(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
        userService.delete(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
