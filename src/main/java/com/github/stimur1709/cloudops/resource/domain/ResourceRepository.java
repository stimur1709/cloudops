package com.github.stimur1709.cloudops.resource.domain;

import java.util.Optional;

public interface ResourceRepository {

    Resource save(Resource resource);

    Optional<Resource> findById(long id);
}

