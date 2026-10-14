package com.villagersociety.leader;

import com.villagersociety.workers.WorkerJob;

/** What the Leader's Staff does when used on a villager. Cycled with Shift + right-click. */
public enum StaffMode {
	MINE("Mineração", WorkerJob.MINE),
	CHOP("Lenhador", WorkerJob.CHOP),
	FARM("Plantação", WorkerJob.FARM),
	MIGRATE("Imigração", null);

	private final String displayName;
	private final WorkerJob job;

	StaffMode(String displayName, WorkerJob job) {
		this.displayName = displayName;
		this.job = job;
	}

	public String displayName() {
		return displayName;
	}

	/** The job this mode assigns, or null for MIGRATE. */
	public WorkerJob job() {
		return job;
	}

	public StaffMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
