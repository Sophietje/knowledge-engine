package interconnect.ke.messaging;

import java.util.UUID;

import interconnect.ke.api.binding.BindingSet;

public class AnswerMessage extends KnowledgeMessage {

	private UUID replyToAskMessage;
	private BindingSet bindings;

	// TODO toString()

}
