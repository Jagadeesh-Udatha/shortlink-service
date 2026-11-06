package dev.jagadeesh.shortlink.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LinkRepository extends MongoRepository<Link, String> {

    List<Link> findAllByOrderByClicksDesc(Pageable page);
}
