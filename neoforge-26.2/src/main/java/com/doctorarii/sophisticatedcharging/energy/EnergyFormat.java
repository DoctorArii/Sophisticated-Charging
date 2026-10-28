package com.doctorarii.sophisticatedcharging.energy;

import java.util.Locale;

public final class EnergyFormat {
	private static final String[] UNITS = {"", "k", "M", "G", "T"};

	private EnergyFormat() {}

	/** 950 -> "950", 12_345 -> "12.3k", 4_000_000 -> "4M". */
	public static String format(long fe) {
		if (fe < 1_000) {
			return Long.toString(fe);
		}
		double value = fe;
		int unit = 0;
		while (value >= 1_000 && unit < UNITS.length - 1) {
			value /= 1_000;
			unit++;
		}
		String number = value >= 100 || value == Math.floor(value) ? String.format(Locale.ROOT, "%.0f", Math.floor(value))
				: String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10);
		return number + UNITS[unit];
	}
}
