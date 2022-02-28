package org.training.networking.chatroom;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

public record Message(String author, LocalDateTime stamp, String note) implements Comparable<Message> {

	public static final Comparator<Message> NATURAL_KEY_COMPARATOR = Comparator.comparing(Message::stamp)
			.thenComparing(Message::author);

	@Override
	public boolean equals(Object o) {
		if (o instanceof Message m) {
			return NATURAL_KEY_COMPARATOR.compare(this, m) == 0;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(author, stamp);
	}

	@Override
	public String toString() {
		return String.format("%s(%tr): %s", author, stamp, note);
	}

	@Override
	public int compareTo(Message o) {
		return NATURAL_KEY_COMPARATOR.compare(this, o);
	}

}
