package com.github.stimur1709.cloudops.credential.api;

import java.net.URI;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.credential.application.CredentialService;
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
@RequestMapping("/api")
public class CredentialController {
    private final CredentialService service;

    public CredentialController(CredentialService service) { this.service = service; }

    @PostMapping("/organizations/{organizationId}/credentials")
    public ResponseEntity<CredentialResponse> create(@PathVariable long organizationId,
            @Valid @RequestBody CredentialRequest request, Authentication authentication) {
        CredentialResponse response = CredentialResponse.from(service.create(organizationId, request.name(),
                request.type(), request.username(), request.secret(), CurrentUser.id(authentication)));
        return ResponseEntity.created(URI.create("/api/credentials/" + response.id())).body(response);
    }

    @GetMapping("/credentials/{id}")
    public CredentialResponse get(@PathVariable long id, Authentication authentication) {
        return CredentialResponse.from(service.get(id, CurrentUser.id(authentication)));
    }

    @PostMapping("/credentials/search")
    public SearchResponse<CredentialResponse> search(@Valid @RequestBody SearchRequest request,
            Authentication authentication) {
        return SearchResponse.from(service.search(request.toQuery(), CurrentUser.id(authentication)),
                CredentialResponse::from);
    }

    @PutMapping("/credentials/{id}")
    public CredentialResponse update(@PathVariable long id, @Valid @RequestBody CredentialRequest request,
            Authentication authentication) {
        return CredentialResponse.from(service.update(id, request.name(), request.type(), request.username(),
                request.secret(), CurrentUser.id(authentication)));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
        service.delete(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
