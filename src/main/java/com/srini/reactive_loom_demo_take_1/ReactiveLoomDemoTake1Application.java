package com.srini.reactive_loom_demo_take_1;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableRedisRepositories(basePackages = "com.srini")
public class ReactiveLoomDemoTake1Application {
	private static final Logger log = LoggerFactory.getLogger(ReactiveLoomDemoTake1Application.class);

	public static void main(String[] args) {
		SpringApplication.run(ReactiveLoomDemoTake1Application.class, args);
	}

	record OmdbResponse (
		@JsonAlias("Title")
		String title,
		@JsonAlias("Genre")
		String genre,
		@JsonAlias("Actors")
		String actors,
		@JsonAlias("Plot")
		String plot,
		String imdbID,
		@JsonAlias("Year")
		Integer year,
		@JsonAlias("Director")
		String director,
		Double imdbRating,
		@JsonAlias("Metascore")
		Integer metascore) implements Serializable { }

	@Component
    static class ImdbClient {
		@Value("${omdb.apikey}")
		private String apiKey;
		WebClient webClient = WebClient.create("http://www.omdbapi.com");
		public OmdbResponse getMovie(String name, Integer year) {
			return webClient.get()
				.uri("/?i=tt3896198&apikey=" + apiKey + "&t=" + name + "&y=" + year + "&type=movie")
				.retrieve()
				.bodyToMono(OmdbResponse.class)
				.block();
		}
	}

	@Service
	static class MovieService {
		private final ImdbClient imdbClient;
		private final RedisDao redisDao;

		public MovieService(ImdbClient imdbClient, RedisDao redisDao) {
			this.imdbClient = imdbClient;
            this.redisDao = redisDao;
        }
		public OmdbResponse getMovie(String name, Integer year) {
			if (redisDao.getMovie(name, year) != null) {
				log.info("Cache hit! ⚡️⚡️");
				return redisDao.getMovie(name, year);
			} else {
				log.info("Cache miss! 🕸️🕸️");
				var movie = imdbClient.getMovie(name, year);
				redisDao.saveMovie(name, year, movie);
				return movie;
			}
		}

		public List<OmdbResponse> getAllMovies() {
			return redisDao.getAllMovies();
		}
	}
	@Bean
	public RedisTemplate<String, OmdbResponse> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, OmdbResponse> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		// Add serializers for keys and values if needed
		return template;
	}

	@Component
	static class RedisDao {
		private RedisTemplate<String, OmdbResponse> redisTemplate;

		public RedisDao(RedisTemplate<String, OmdbResponse> redisTemplate) {
			this.redisTemplate = redisTemplate;
		}

		public List<OmdbResponse> getAllMovies() {
            return redisTemplate.opsForList().range("*",0, -1);
		}

		public void saveMovie(String name, int year, OmdbResponse movie) {
			redisTemplate.opsForValue().set(name + year, movie);
		}

		public OmdbResponse getMovie(String name, int year) {
			return redisTemplate.opsForValue().get(name + year);
		}
	}

	@RestController
	class MovieController {
		private final MovieService movieService;

        MovieController(MovieService movieService) {
            this.movieService = movieService;
        }

		@GetMapping("/all")
        public List<OmdbResponse> getAllMovies() {
			return movieService.getAllMovies();
		}

		@GetMapping("/getMovie")
		public OmdbResponse getMovie(@RequestParam String name, @RequestParam Integer year) {
			return movieService.getMovie(name, year);
		}
	}

}
