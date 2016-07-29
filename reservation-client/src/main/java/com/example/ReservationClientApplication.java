package com.example;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@EnableCircuitBreaker
@EnableZuulProxy
@EnableDiscoveryClient
@SpringBootApplication
public class ReservationClientApplication {

	@Bean
	CommandLineRunner runner(DiscoveryClient discoveryClient) {
		return args -> discoveryClient.getInstances("reservation-service")
				.forEach(si -> System.out.println(String.format("%s %s:%s", si.getServiceId(), si.getHost(), si.getPort())));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}

/*
 * has to create a config to define RestTemplate since it is not created by Spring no more.
 * https://github.com/spring-cloud/spring-cloud-commons/blob/master/docs/src/main/asciidoc/spring-cloud-commons.adoc#spring-resttemplate-as-a-load-balancer-client
 */
@org.springframework.context.annotation.Configuration
class Configuration {

	@LoadBalanced
	@Bean
	RestTemplate rt() {
		return new RestTemplate();
	}
}

@RestController
@RequestMapping("/reservations")
class ReservationApiGatewayRestController {

	@Autowired
	@LoadBalanced
	private RestTemplate rt;

	public Collection<String> getReservationNamesFallback() {
		return Collections.emptyList();
	}

	@RequestMapping("/names")
	@HystrixCommand(fallbackMethod = "getReservationNamesFallback")
	public Collection<String> getReservationNames() {
		ParameterizedTypeReference<Resources<Reservation>> parameterizedTypeReference =
				new ParameterizedTypeReference<Resources<Reservation>>() {
				};

		ResponseEntity<Resources<Reservation>> exchange =
			rt.exchange("http://reservation-service/reservations", HttpMethod.GET, null, parameterizedTypeReference);

		return exchange.getBody().getContent().stream().map(Reservation::getReservationName).collect(Collectors.toList());
	}
}

class Reservation {

	Long id;
	String reservationName;
	public Long getId() {
		return id;
	}
	public String getReservationName() {
		return reservationName;
	}
}
