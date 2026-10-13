package com.villagersociety.client;

import com.villagersociety.network.ReputationPayload;

/** Last reputation data received from the server (display only). */
final class ClientReputationState {
	private static volatile ReputationPayload current = ReputationPayload.NONE;

	private ClientReputationState() {
	}

	static ReputationPayload get() {
		return current;
	}

	static void set(ReputationPayload payload) {
		current = payload;
	}

	static void clear() {
		current = ReputationPayload.NONE;
	}
}
