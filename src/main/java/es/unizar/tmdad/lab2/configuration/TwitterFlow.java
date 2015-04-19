package es.unizar.tmdad.lab2.configuration;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.support.Function;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.social.twitter.api.StreamListener;
import org.springframework.social.twitter.api.Tweet;

import es.unizar.tmdad.lab2.domain.MyTweet;
import es.unizar.tmdad.lab2.domain.TargetedTweet;
import es.unizar.tmdad.lab2.service.TwitterLookupService;

@Configuration
@EnableIntegration
@IntegrationComponentScan
@ComponentScan
public class TwitterFlow {

	@Autowired
	private TwitterLookupService lookupService;

	@Bean
	public DirectChannel requestChannel() {
		return new DirectChannel();
	}

	@Bean
	public IntegrationFlow sendTweet() {
		// Transforms the specified Tweet to a new TargetedTweet including the queries contained in the Tweet
		GenericTransformer<Tweet, TargetedTweet> targetedTweetTransform = (Tweet tweet) -> {
			
			// Retrieves the queries which are contained in the Tweet
			List<String> retrieveQueries = lookupService.getQueries()
					.stream()
					.filter(query -> tweet.getText().contains(query))
					.collect(Collectors.toList());
			
			return new TargetedTweet(new MyTweet(tweet), retrieveQueries);
		};

		// Splits the specified Tweet in a List of Tweets
		Function<TargetedTweet, ?> targetedTweetSplit = tweet ->{  
			return tweet.getTargets()
					.stream()
					.map(query -> new TargetedTweet(tweet.getTweet(), query))
					.collect(Collectors.toList());
		};
		
		// Adds the bold type to the contained query of the Tweet
		GenericTransformer<TargetedTweet, TargetedTweet> tweetTextTransform = (TargetedTweet tweet) ->{ 
			String query = tweet.getFirstTarget();
			String text = tweet.getTweet().getText().replace(query, "<b>"+query+"</b>");
			tweet.getTweet().setUnmodifiedText(text);
			return tweet;
			};
			
		return IntegrationFlows.from(requestChannel())
				.filter(object -> object instanceof Tweet)
				.transform(targetedTweetTransform)
				.split(TargetedTweet.class,  targetedTweetSplit)
				.transform(tweetTextTransform)
				.handle("streamSendingService", "sendTweet")
				.get();
	}

}

@MessagingGateway(name = "integrationStreamListener", defaultRequestChannel = "requestChannel")
interface MyStreamListener extends StreamListener {

}
