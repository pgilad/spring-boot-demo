package com.example.demo.projects;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMongoRepository extends ReactiveMongoRepository<Project, String> {
}
