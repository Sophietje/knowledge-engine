package interconnect.ke.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.PrefixMappingMem;
import org.junit.jupiter.api.Test;

import interconnect.ke.api.binding.Binding;
import interconnect.ke.api.binding.BindingSet;
import interconnect.ke.api.interaction.PostKnowledgeInteraction;
import interconnect.ke.api.interaction.ReactKnowledgeInteraction;

public class TestPostReact {
	@Test
	public void testPostReact() throws InterruptedException {
		PrefixMappingMem prefixes = new PrefixMappingMem();
		prefixes.setNsPrefixes(PrefixMapping.Standard);
		prefixes.setNsPrefix("ex", "https://www.tno.nl/example/");

		int wait = 2;
		final CountDownLatch kb2Initialized = new CountDownLatch(1);
		final CountDownLatch kb2ReceivedKnowledge = new CountDownLatch(1);

		// the knowledge base that posts the data.
		KnowledgeBase kb1 = new MockedKnowledgeBase("kb1") {
			private PostKnowledgeInteraction ki;
			@Override
			public void smartConnectorReady(SmartConnector aSC) {
				GraphPattern gp = new GraphPattern(prefixes, "?a ex:b ?c.");
				this.ki = new PostKnowledgeInteraction(new CommunicativeAct(), gp, null);
				aSC.register(this.ki);

				waitForOtherKbAndPostSomething();
			};

			private void waitForOtherKbAndPostSomething() {
				// Wait until KB2 completed its latch.
				try {
					assertTrue(kb2Initialized.await(wait, TimeUnit.SECONDS),
							"kb2 should have been initialized within " + wait + " seconds.");
				} catch (InterruptedException e) {
					fail("Should not throw any exception.");
				}

				BindingSet bindingSet = new BindingSet();
				Binding binding = new Binding();
				binding.put("a", "<https://www.tno.nl/example/a>");
				binding.put("c", "<https://www.tno.nl/example/c>");
				bindingSet.add(binding);
				
				this.getSmartConnector().post(ki, bindingSet);
			}
		};

		// the knowledge base that receives the data
		KnowledgeBase kb2 = new MockedKnowledgeBase("kb2") {
			@Override
			public void smartConnectorReady(SmartConnector aSC) {
				GraphPattern gp = new GraphPattern(prefixes, "?a ex:b ?c.");
				ReactKnowledgeInteraction ki = new ReactKnowledgeInteraction(new CommunicativeAct(), gp, null);

				aSC.register(ki, new ReactHandler() {
					@Override
					public BindingSet react(ReactKnowledgeInteraction anRKI, BindingSet argument) {
						Iterator<Binding> iter = argument.iterator();
						Binding b = iter.next();
						
						assertEquals("https://www.tno.nl/example/a", b.get("a"), "Binding of 'a' is incorrect.");
						assertEquals("https://www.tno.nl/example/c", b.get("c"), "Binding of 'c' is incorrect.");
						
						assertFalse(iter.hasNext(), "This BindingSet should only have a single binding.");

						// Complete the latch to make the test pass.
						kb2ReceivedKnowledge.countDown();

						return null;
					}
				});
			};
		};

		assertTrue(kb2ReceivedKnowledge.await(wait, TimeUnit.SECONDS), "Should execute the tests within " + wait + " seconds.");
	}
}