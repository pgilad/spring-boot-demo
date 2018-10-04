package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class HelloController {

    private static String story = "the quick brown fox jumped over the lazy fence and then noticed another quick black fox " +
            "that was much quicker than the original fox but the original fox was able to jump higher over the fence";

    @GetMapping("/hello")
    public Mono<String> sayHello() {
        return Mono.just("Hello");
    }

    @GetMapping("/word-count/v1")
    public Flux<Tuple2<String, Long>> wordCount1(@RequestParam(defaultValue = "2") Integer limit) {
        String[] words = story.split(" ");
        HashMap<String, Long> counts = new HashMap<>();
        for (String word : words) {
            Long count = counts.getOrDefault(word, 0L) + 1;
            counts.put(word.toLowerCase(), count);
        }
        Set<Long> values = new HashSet<>(counts.values());
        final ArrayList<Long> sortedCounts = new ArrayList<>(values);
        sortedCounts.sort(Collections.reverseOrder());

        ArrayList<Tuple2<String, Long>> response = new ArrayList<>();
        for (var i = 0; i < Math.min(limit, sortedCounts.size()); i++) {
            Long count = sortedCounts.get(i);
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                if (entry.getValue().equals(count)) {
                    response.add(Tuples.of(entry.getKey(), count));
                }
            }
        }

        return Flux.fromIterable(response);
    }

    @GetMapping("/word-count/v2")
    public Flux<Tuple2<String, Long>> wordCount2(@RequestParam(defaultValue = "2") Integer limit) {
        String[] words = story.split(" ");

        HashMap<String, Long> counts = new HashMap<>();
        TreeMap<String, Long> sortedCounts = new TreeMap<String, Long>(Comparator.comparing(counts::get, Comparator.reverseOrder()));

        for (String word : words) {
            counts.merge(word.toLowerCase(), 1L, Math::addExact);
        }
        sortedCounts.putAll(counts);
        ArrayList<Tuple2<String, Long>> response = new ArrayList<>();
        for (var i = 0; i < limit; i++) {
            Map.Entry<String, Long> entry = sortedCounts.pollFirstEntry();
            if (entry == null) {
                // no more entries in map
                break;
            }
            response.add(Tuples.of(entry.getKey(), entry.getValue()));
        }

        return Flux.fromIterable(response);
    }

    @GetMapping("/word-count/v3")
    public Flux<Tuple2<String, Long>> wordCount3(@RequestParam(defaultValue = "2") Integer limit) {
        return Flux.fromArray(story.split(" "))
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .flatMapIterable(Map::entrySet)
                .sort((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(a -> Tuples.of(a.getKey(), a.getValue()))
                .take(limit);
    }
}
