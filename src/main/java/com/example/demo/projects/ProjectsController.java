package com.example.demo.projects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.time.Duration;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(path = "/api/projects")
public class ProjectsController {

    private final ProjectMongoRepository mongo;

    @Autowired
    public ProjectsController(ProjectMongoRepository mongo) {
        this.mongo = mongo;
    }

    @GetMapping
    private Flux<Project> getProjects() {
        return mongo.findAll();
    }

    @GetMapping(value = "/stream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    private Flux<Project> getProjectsStream() {
        return mongo.findAll().delayElements(Duration.ofSeconds(1));
    }

    @PostMapping
    private Mono<Project> createProject(@RequestBody @Valid Project project) {
        return mongo.save(project);
    }

    @GetMapping("/{id}")
    private Mono<ResponseEntity<Project>> getProject(@PathVariable String id) {
        return mongo.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    private Mono<ResponseEntity<Project>> updateProject(@PathVariable String id, @RequestBody Project project) {
        return mongo.findById(id)
                .flatMap(existingProject -> {
                    existingProject.setName(project.getName());
                    existingProject.setDescription(project.getDescription());

                    return mongo.save(existingProject);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    private Mono<ResponseEntity<Object>> deleteProject(@PathVariable String id) {
        return mongo.findById(id)
                .flatMap(project -> {
                    return mongo
                            .delete(project)
                            .thenReturn(ResponseEntity.noContent().build());
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<List<String>> handleWebExchangeBindException(WebExchangeBindException e) {
        return Flux
                .fromIterable(e.getFieldErrors())
                .map(field -> String.format("%s.%s %s", e.getObjectName(), field.getField(), field.getDefaultMessage()))
                .collectList();
    }
}
