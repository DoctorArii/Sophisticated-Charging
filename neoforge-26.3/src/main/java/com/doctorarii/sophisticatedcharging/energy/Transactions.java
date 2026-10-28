package com.doctorarii.sophisticatedcharging.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Opening a transaction from code that may or may not already run inside one. */
public final class Transactions {
	private Transactions() {}

	/**
	 * A new transaction: a root one, or one nested in the transaction that is open on this thread right now. Null while a
	 * transaction is closing (nothing can be opened then); callers treat that as "can't do it now".
	 */
	@Nullable
	@SuppressWarnings("deprecation") // getCurrentOpenedTransaction: we really don't get the open transaction passed in here
	public static Transaction open() {
		return switch (Transaction.getLifecycle()) {
			case NONE, ROOT_CLOSING -> Transaction.openRoot();
			case OPEN -> Transaction.open(Transaction.getCurrentOpenedTransaction());
			case CLOSING -> null;
		};
	}
}
