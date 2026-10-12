package com.villagersociety.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.villagersociety.VillagerSociety;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Editable configuration (config/villager_society.json).
 * Every value is clamped by {@link #validate()} so a bad file can never break the server.
 * Plain data + pure logic only: no Minecraft classes, so it is unit-testable.
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// ---- Reputation deltas per action (before personality / severity multipliers) ----
	public int repTradeCompleted = 1;
	public int repReplantCrop = 1;
	public int repDonateItem = 2;
	public int repDefendVillager = 5;
	public int repKillMonsterNearVillage = 1;
	public int repRepairDamage = 2;
	public int repBreakCrop = -3;
	public int repBreakBedOrWorkstation = -10;
	public int repStealContainer = -8;
	public int repAttackVillager = -15;
	public int repKillVillager = -40;
	/** Extra penalty multiplier applied when repeating an offense after a warning. */
	public double repeatOffenseMultiplier = 1.5;
	/** Global punishment intensity (0.1 - 3.0). */
	public double punishmentIntensity = 1.0;

	// ---- Reputation recovery ----
	/** Ticks between passive recovery steps for negative reputation of minor offenses. */
	public int recoveryIntervalTicks = 24000;
	public int recoveryPerInterval = 1;
	/** Grave offenses block passive recovery for this many ticks. */
	public int graveOffenseLockTicks = 24000 * 14;

	// ---- Distances ----
	public double witnessDistance = 16.0;
	public int villageInfluenceRadius = 48;
	public int villageAssociationMaxDistance = 64;

	// ---- Communication between villagers ----
	public int gossipRadius = 24;
	public int gossipMaxHops = 2;
	public int gossipDelayTicks = 100;
	/** If true, villages share (diluted) reports with other villages. */
	public boolean crossVillageGossip = false;
	public double crossVillageGossipFactor = 0.25;

	// ---- Rebellion ----
	public int rebellionMinParticipants = 2;
	public int rebellionMaxParticipants = 8;
	public int aiUpdateIntervalTicks = 10;
	public int pursuitMaxDurationTicks = 20 * 60;
	public int pursuitMaxDistance = 64;
	public double meleeDamage = 3.0;
	public double meleeRange = 2.0;
	public int meleeCooldownTicks = 20;
	/** Suspicion level (reputation) at which each rebellion phase starts. */
	public int suspicionThreshold = -20;
	public int warningThreshold = -35;
	public int mobilizationThreshold = -50;
	public int rebellionThreshold = -70;

	// ---- Economy ----
	/** Max discount as a fraction of the base price (0.0 - 0.5). */
	public double maxDiscount = 0.25;
	public double friendDiscount = 0.05;
	public double benefactorDiscount = 0.12;
	public double heroDiscount = 0.20;
	public boolean refuseTradeDuringRebellion = true;

	// ---- Recommendation letter ----
	public int letterMinReputation = 80;
	public int letterMinTrades = 10;
	public int letterMinGoodDeeds = 3;
	public int letterTrustBonus = 20;
	public int letterTrustBonusUnknownVillage = 8;
	public boolean letterSingleUse = true;

	// ---- History limits ----
	public int maxHistoryEntriesPerPlayer = 30;

	// ---- Debug ----
	/** Enables /villagersociety debug <action> (changes your own reputation; for testing only). */
	public boolean debugCommands = false;

	/** Clamps every field into a safe range. */
	public void validate() {
		repTradeCompleted = clamp(repTradeCompleted, 0, 10);
		repReplantCrop = clamp(repReplantCrop, 0, 10);
		repDonateItem = clamp(repDonateItem, 0, 20);
		repDefendVillager = clamp(repDefendVillager, 0, 30);
		repKillMonsterNearVillage = clamp(repKillMonsterNearVillage, 0, 10);
		repRepairDamage = clamp(repRepairDamage, 0, 20);
		repBreakCrop = clamp(repBreakCrop, -50, 0);
		repBreakBedOrWorkstation = clamp(repBreakBedOrWorkstation, -100, 0);
		repStealContainer = clamp(repStealContainer, -100, 0);
		repAttackVillager = clamp(repAttackVillager, -100, 0);
		repKillVillager = clamp(repKillVillager, -100, 0);
		repeatOffenseMultiplier = clamp(repeatOffenseMultiplier, 1.0, 5.0);
		punishmentIntensity = clamp(punishmentIntensity, 0.1, 3.0);

		recoveryIntervalTicks = clamp(recoveryIntervalTicks, 1200, 24000 * 60);
		recoveryPerInterval = clamp(recoveryPerInterval, 0, 20);
		graveOffenseLockTicks = clamp(graveOffenseLockTicks, 0, 24000 * 365);

		witnessDistance = clamp(witnessDistance, 4.0, 64.0);
		villageInfluenceRadius = clamp(villageInfluenceRadius, 16, 128);
		villageAssociationMaxDistance = clamp(villageAssociationMaxDistance, villageInfluenceRadius, 256);

		gossipRadius = clamp(gossipRadius, 4, 64);
		gossipMaxHops = clamp(gossipMaxHops, 0, 5);
		gossipDelayTicks = clamp(gossipDelayTicks, 0, 1200);
		crossVillageGossipFactor = clamp(crossVillageGossipFactor, 0.0, 1.0);

		rebellionMinParticipants = clamp(rebellionMinParticipants, 1, 16);
		rebellionMaxParticipants = clamp(rebellionMaxParticipants, rebellionMinParticipants, 24);
		aiUpdateIntervalTicks = clamp(aiUpdateIntervalTicks, 2, 100);
		pursuitMaxDurationTicks = clamp(pursuitMaxDurationTicks, 200, 20 * 60 * 30);
		pursuitMaxDistance = clamp(pursuitMaxDistance, 16, 256);
		meleeDamage = clamp(meleeDamage, 0.0, 10.0);
		meleeRange = clamp(meleeRange, 1.0, 4.0);
		meleeCooldownTicks = clamp(meleeCooldownTicks, 5, 200);

		// Thresholds must be strictly ordered: suspicion > warning > mobilization > rebellion.
		rebellionThreshold = clamp(rebellionThreshold, -100, -4);
		mobilizationThreshold = clamp(mobilizationThreshold, rebellionThreshold + 1, -3);
		warningThreshold = clamp(warningThreshold, mobilizationThreshold + 1, -2);
		suspicionThreshold = clamp(suspicionThreshold, warningThreshold + 1, -1);

		maxDiscount = clamp(maxDiscount, 0.0, 0.5);
		friendDiscount = clamp(friendDiscount, 0.0, maxDiscount);
		benefactorDiscount = clamp(benefactorDiscount, friendDiscount, maxDiscount);
		heroDiscount = clamp(heroDiscount, benefactorDiscount, maxDiscount);

		letterMinReputation = clamp(letterMinReputation, 1, 100);
		letterMinTrades = clamp(letterMinTrades, 0, 1000);
		letterMinGoodDeeds = clamp(letterMinGoodDeeds, 0, 1000);
		letterTrustBonus = clamp(letterTrustBonus, 0, 50);
		letterTrustBonusUnknownVillage = clamp(letterTrustBonusUnknownVillage, 0, letterTrustBonus);

		maxHistoryEntriesPerPlayer = clamp(maxHistoryEntriesPerPlayer, 5, 200);
	}

	// ------------------------------------------------------------------ IO

	private static ModConfig instance = new ModConfig();

	public static ModConfig get() {
		return instance;
	}

	/** Loads the config from disk, creating it with defaults if missing or unreadable. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("villager_society.json");
		ModConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, ModConfig.class);
			} catch (Exception e) {
				VillagerSociety.LOGGER.error("Could not read {}; using defaults.", path, e);
			}
		}
		if (loaded == null) {
			loaded = new ModConfig();
		}
		loaded.validate();
		instance = loaded;
		// Rewrite so new fields appear and clamped values are persisted.
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(loaded, writer);
		} catch (IOException e) {
			VillagerSociety.LOGGER.error("Could not write {}", path, e);
		}
	}

	static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	static double clamp(double v, double min, double max) {
		if (Double.isNaN(v)) {
			return min;
		}
		return Math.max(min, Math.min(max, v));
	}
}
