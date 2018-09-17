# Spring Boot Webflux With Reactive Mongo

> A guide to bootstrapping a basic Spring Boot Webflux Application, with Reactive Mongo

## Prerequisites

- IntelliJ IDEA 2018.2.3 (Ultimate Edition) or newer
- Gradle 4.10.1 or newer (Use Sdkman to install)
- Java 10.0.2 or newer (Use Sdkman to install)
- Lombok plugin for IntelliJ
- `git`
- `httpie` (`brew install httpie`) (Optional)
- `mongodb` for later

## Setup

- [https://start.spring.io/](https://start.spring.io/)
- Gradle, Java 10
- Add dependencies:
  - Reactive Web
  - Actuator
  - DevTools
  - Lombok
- Download & unzip archive into ~/repos/spring-boot-demo
- Start IntelliJ
- Setup + Add `Annotation Processors -> Enable annotation processing`
- Run application (*hint*: currently it does nothing)

## Hello World

Let's add a simple controller with mapping to respond to an `hello` request from the client:

Create a new file named `HelloController.java`:

```java
@RestController
public class HelloController {

    @GetMapping("/hello")
    public Mono<String> sayHello() {
        return Mono.just("Hello");
    }
}
```

Restart the application, and now let's get our first response back from the server:

```bash
$ http localhost:8080/hello
HTTP/1.1 200 OK
Content-Length: 6
Content-Type: text/plain;charset=UTF-8

Hello
```

We have a simple Hello World application. :smile:

## Adding logs

Annotate any class with `@Sl4f` and add a log:

`log.info("Here");`

This uses `Lombok` which does annotation processing which re-writes your source code behind the screens.

This actually adds a line like this to your class:

`private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LogExample.class);`

We will see how `Lombok` will actually aid us in saving a lot of boilerplate code (Which Java is known for).

## Changing the port

The easiest way to change the port, is by overriding `server.port` in `application.properties`:

`server.port=9090`

Go ahead and re-run your application, now it runs on `9090`.

## Health checks

A basic health check is setup due to `spring-boot-actuator`. Let's give it a test:

```bash
$ http localhost:9090/actuator/health
HTTP/1.1 200 OK
Content-Length: 15
Content-Type: application/vnd.spring-boot.actuator.v2+json;charset=UTF-8

{
    "status": "UP"
}
```

We can easily extend this health check to include our custom health checks:

Add another file `health/CustomHealthCheck.java`:

```java
@Component
public class CustomHealthCheck implements ReactiveHealthIndicator {

    @Override
    public Mono<Health> health() {
        return Mono.just(Health.up().withDetail("Service", "Good!").build());
    }
}
```

In order to see full-details on health API, we need to add the following to our `application.properties`:

```ini
management.endpoint.health.show-details=ALWAYS
```

Now let's try our health API again:

```bash
$ http localhost:9090/actuator/health
HTTP/1.1 200 OK
Content-Length: 195
Content-Type: application/vnd.spring-boot.actuator.v2+json;charset=UTF-8

{
    "details": {
        "customHealthCheck": {
            "details": {
                "Service": "Good!"
            },
            "status": "UP"
        },
        "diskSpace": {
            "details": {
                "free": 190510637056,
                "threshold": 10485760,
                "total": 499963174912
            },
            "status": "UP"
        }
    },
    "status": "UP"
}
```

## Info APIs

Let's assume we want to show various information about our app. This is revealed in the following `actuator` API:

```bash
$ http localhost:9090/actuator/info
HTTP/1.1 200 OK
Content-Length: 2
Content-Type: application/vnd.spring-boot.actuator.v2+json;charset=UTF-8

{}
```

Currently we are exposing nothing. But let's add some general info. Add the following to your `application.properties`:

```ini
info.coolness.spring=agile
```

If we run the same `httpie` request now:

```bash
$ http localhost:9090/actuator/info
HTTP/1.1 200 OK
Content-Length: 31
Content-Type: application/vnd.spring-boot.actuator.v2+json;charset=UTF-8

{
    "coolness": {
        "spring": "agile"
    }
}
```

That was easy. Now let's add some info with logic:

Add a new file named `info/InfoContrib.java`:


```java
@Component
public class InfoContrib implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("contributor", "foo");
    }
}
```

Now when we run a request:

```bash
$ http localhost:9090/actuator/info
HTTP/1.1 200 OK
Content-Length: 51
Content-Type: application/vnd.spring-boot.actuator.v2+json;charset=UTF-8

{
    "contributor": "foo",
    "coolness": {
        "spring": "agile"
    }
}
```

And off course there are easy ways to auto add application information (such as git, version, etc...)

Enough with all this setup, it's cool, but where's the real stuff?

## Adding Reactive Mongo

Add the following line to your `build.gradle` under `dependencies`:

`compile('org.springframework.boot:spring-boot-starter-data-mongodb-reactive')`

And let's add the annotations to support Reactive Mongo:

```java

```

`@EnableReactiveMongoRepositories` and `@EnableMongoAuditing` under main app.

Let's create our domain document, `Project`:

```java
@Document
@Data
public class Project {

    @Id
    private String id;

    private String name;

    private String description;

    @CreatedDate
    private LocalDateTime createdAt;
}
```

`@Document` is a mongo annotation, while `@Data` is a magic `Lombok` annotation which auto creates lots of stuff for us
(getters, setters, all args constructor and some other magic).

Everything is now in place to wire up our controller with mongo.

First let's create a repository interface, almost everything we need to work with mongo (or reactive mongo in our case):

`repository/ProjectMongoRepository.java`:

```java
@Repository
public interface ProjectMongoRepository extends ReactiveMongoRepository<Project, String> {
}
```

Let's create our very own `controller/ProjectController.java`:

```java
@RestController
@RequestMapping("/api/projects")
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
}
```

This is a pretty simple CRUD example using Reactive Mongo. Notice how all of the Mongo operations:

1. Are already defined by our Repository interface (and ready to use)
2. Return a `Flux | Mono`

This means that we can run any stream operation on them because they act as an Publisher.

One thing that comes to mind, is what about validation? How do we ensure that the client sends us the expected data?

Well, we already handle missing entities, but we haven't handled properties validation. (Permissions and security will be on a different guide).

## Validation

Let's decide that `project.name` cannot be null or an empty string. This makes sense as we don't want to save projects without names
We do allow empty `project.description` though, but we want to limit it to maximum of 100 characters. We also want to limit
`project.name` length to 50 characters.

This is easily added using `JSR-303` bean validation, already included in Spring Boot Web(flux).

Let's revisit our `Project` model:

```java
@Data
@Document
class Project {

    @Id
    private String id;

    @NotBlank
    @Length(min = 1, max = 30)
    private String name;

    @Length(max = 100)
    private String description;

    @CreatedDate
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
}
```

Notice how we add annotation over the fields. This annotation based validation makes our life very easy. We can also
create custom validations (using annotations) but we'll leave that for another lesson.

Notice also how we added `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` to the `createdAt` field - this ensures
that this field is only serialized (when being field is being read) but never when writing to class. This prevents the user
from feeding his own `createdAt` data, interfering our mongo auditing.

One last thing we will need to add is a proper error validation formatting (the default is way too verbose). Add the following
method to the `ProjectsController`:

```java
@ExceptionHandler(WebExchangeBindException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public Mono<List<String>> handleWebExchangeBindException(WebExchangeBindException e) {
    return Flux
            .fromIterable(e.getFieldErrors())
            .map(field -> String.format("%s.%s %s", e.getObjectName(), field.getField(), field.getDefaultMessage()))
            .collectList();
}
```

And that's it. Give it a try with:

```bash
$ http POST :9090/api/projects/ name='' description='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
HTTP/1.1 400 Bad Request
Content-Length: 136
Content-Type: application/json;charset=UTF-8

[
    "project.name length must be between 1 and 30",
    "project.description length must be between 0 and 100",
    "project.name must not be blank"
]
```

Yep we got sweet validation.

## Last play - Server Sent Events (SSE)

Let's add another method to our controller, that can stream projects back to the client:

```java
@GetMapping(value = "/stream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
private Flux<Project> getProjectsStream() {
    return mongo.findAll().delayElements(Duration.ofSeconds(1));
}
```

We simulate a delay in responding to the client. In order to request the data make sure you client is not waiting for the stream to end, for example:

```bash
$ http -S :9090/api/projects/stream
```

And that's how we added SSE support!

## Where to go from here

We have only just glimpsed the surface of Spring Boot. Off course there are many other modules and extension to it.
Several key concepts come to mind:

1. Security (`spring-boot-security`)
2. Permissions
3. Pagination
4. HATEOS
5. Testing!!
6. Packaging and releasing
7. Dockerizing the app
8. Monitoring (and metrics)
9. Scale
10. Configuration, development and production

Much much more...

Keep learning and having fun, and share your success (or frustrations) with Spring Boot!!!

## License

MIT


