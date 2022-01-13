package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock
 * acquisition for the user (you, in the last task of Part 2). Generally
 * speaking, you should use LockUtil for lock acquisition instead of calling
 * LockContext methods directly.
 */
public class LockUtil {
    /**
     * Ensure that the current transaction can perform actions requiring
     * `requestType` on `lockContext`.
     *
     * `requestType` is guaranteed to be one of: S, X, NL.
     *
     * This method should promote/escalate/acquire as needed, but should only
     * grant the least permissive set of locks needed. We recommend that you
     * think about what to do in each of the following cases:
     * - The current lock type can effectively substitute the requested type
     * - The current lock type is IX and the requested lock is S
     * - The current lock type is an intent lock
     * - None of the above: In this case, consider what values the explicit
     *   lock type can be, and think about how ancestor looks will need to be
     *   acquired or changed.
     *
     * You may find it useful to create a helper method that ensures you have
     * the appropriate locks on all ancestors.
     */
    public static void ensureSufficientLockHeld(LockContext lockContext, LockType requestType) {
        // requestType must be S, X, or NL
        assert (requestType == LockType.S || requestType == LockType.X || requestType == LockType.NL);

        // Do nothing if the transaction or lockContext is null
        TransactionContext transaction = TransactionContext.getTransaction();
        if (transaction == null || lockContext == null) return;

        // You may find these variables useful
        LockContext parentContext = lockContext.parentContext();
        LockType effectiveLockType = lockContext.getEffectiveLockType(transaction);
        LockType explicitLockType = lockContext.getExplicitLockType(transaction);

        // TODO(proj4_part2): implement
        if (LockType.substitutable(effectiveLockType, requestType)) {
            // The current lock type can effectively substitute the requested type
            return;
        } else if (effectiveLockType == LockType.IX && requestType == LockType.S) {
            // The current lock type is IX and the requested lock is S
            // Piazza @452_f17
            lockContext.promote(transaction, LockType.SIX);
        } else if (effectiveLockType.isIntent()) {
            // The current lock type is an intent lock
            // Ex: IS(database) -> IS(table) -> S(table/1)
            // ensureSufficientLockHeld(table, X)
            // IX(database) -> X(table)
            if (parentContext != null)
                updateAncestors(parentContext, transaction, requestType);
            lockContext.escalate(transaction);
        } else {
            // None of the above: In this case, consider what values the explicit
            // lock type can be, and think about how ancestor looks will need to be
            // acquired or changed.
            if (parentContext != null)
                updateAncestors(parentContext, transaction, requestType);

            // If our current lock type is NL, we need to acquire
            // however if we are holding a lock, we need to promote.
            if (explicitLockType == LockType.NL) {
                lockContext.acquire(transaction, requestType);
            } else {
                lockContext.promote(transaction, requestType);
            }
        }

        return;
    }

    // TODO(proj4_part2) add any helper methods you want
    private static void updateAncestors(LockContext parent, TransactionContext transaction,
                                 LockType requestType) {

        LockContext grandParent = parent.parent;

        if (grandParent != null) {
            // We will keep going up recursively until we reach the root that has no parent
            updateAncestors(grandParent, transaction, requestType);
        }


        // Once we are in the root, we do our parent promotion
        // Then the recursion will just try to recursively go back to
        // whoever called it.
        LockType parentLock = parent.lockman.getLockType(transaction, parent.name);
        LockType newParentLock = parentLock;
        if (!LockType.canBeParentLock(parentLock, requestType))
            newParentLock = LockType.parentLock(requestType);

        if (parentLock == LockType.NL) {
            parent.acquire(transaction, newParentLock);
        } else if (parentLock != newParentLock) {
            if (parentLock == LockType.S && newParentLock == LockType.IX)
                newParentLock = LockType.SIX;
            parent.promote(transaction, newParentLock);
        }

        return;
    }

}
