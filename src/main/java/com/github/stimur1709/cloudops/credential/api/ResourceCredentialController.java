package com.github.stimur1709.cloudops.credential.api;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.application.ResourceCredentialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/credentials")
public class ResourceCredentialController {

    private final ResourceCredentialService service;

    public ResourceCredentialController(ResourceCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResourceCredentialResponse> getAll(@PathVariable long resourceId, Authentication authentication) {
        return service.getAll(resourceId, CurrentUser.id(authentication));
    }

    @PutMapping("/{purpose}")
    public ResourceCredentialResponse bind(
            @PathVariable long resourceId,
            @PathVariable CredentialPurpose purpose,
            @Valid @RequestBody BindCredentialRequest request,
            Authentication authentication) {
        return service.bind(resourceId, purpose, request.credentialId(), CurrentUser.id(authentication));
    }

    @DeleteMapping("/{purpose}")
    public ResponseEntity<Void> unbind(
            @PathVariable long resourceId, @PathVariable CredentialPurpose purpose, Authentication authentication) {
        service.unbind(resourceId, purpose, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
