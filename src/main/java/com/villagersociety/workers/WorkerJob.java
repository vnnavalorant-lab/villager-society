package com.villagersociety.workers;

/** Jobs a leader can give to a villager. */
public enum WorkerJob {
	MINE("mine", "Mineração"),
	CHOP("chop", "Lenhador"),
	FARM("farm", "Plantação");

	private final String id;
	private final String displayName;

	WorkerJob(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	/** Unknown ids (e.g. from an older save) fall back to MINE instead of failing. */
	public static WorkerJob fromId(String id) {
		for (WorkerJob job : values()) {
			if (job.id.equals(id)) {
				return job;
			}
		}
		return MINE;
	}
}
