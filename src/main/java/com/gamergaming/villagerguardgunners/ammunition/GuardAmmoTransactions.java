package com.gamergaming.villagerguardgunners.ammunition;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Simulate-before-commit item movement for bounded ammunition inventories.
 * Callers remain responsible for checking TaCZ compatibility before transfer.
 */
public final class GuardAmmoTransactions {
    private static final int MAX_TRANSACTION_SLOTS = 1_024;

    private GuardAmmoTransactions() {
    }

    public static InsertionPlan planInsertion(
            IItemHandlerModifiable destination,
            ItemStack offered) {
        if (destination == null || offered == null || offered.isEmpty()) {
            return InsertionPlan.invalid(offered);
        }

        HandlerSnapshot snapshot = HandlerSnapshot.capture(destination);
        if (snapshot == null) {
            return InsertionPlan.invalid(offered);
        }

        try {
            ItemStack remainder = insertAcross(
                    destination,
                    offered,
                    true,
                    snapshot.stacks().size());
            if (!snapshot.matches(destination)) {
                return InsertionPlan.failed(
                        offered,
                        snapshot.restoreSafely(destination)
                                ? PlanStatus.ROLLED_BACK
                                : PlanStatus.ROLLBACK_FAILED);
            }
            return InsertionPlan.valid(offered, remainder);
        } catch (RuntimeException exception) {
            return InsertionPlan.failed(
                    offered,
                    snapshot.restoreSafely(destination)
                            ? PlanStatus.ROLLED_BACK
                            : PlanStatus.ROLLBACK_FAILED);
        }
    }

    public static InsertionResult insert(
            IItemHandlerModifiable destination,
            ItemStack offered) {
        InsertionPlan plan = planInsertion(destination, offered);
        if (!plan.valid()) {
            return new InsertionResult(
                    transactionStatus(plan.status()),
                    0,
                    copyOrEmpty(offered));
        }
        if (plan.accepted() == 0) {
            return new InsertionResult(TransactionStatus.NO_OP, 0, plan.remainder());
        }

        HandlerSnapshot snapshot = HandlerSnapshot.capture(destination);
        if (snapshot == null) {
            return new InsertionResult(TransactionStatus.INVALID_HANDLER, 0, copyOrEmpty(offered));
        }

        try {
            ItemStack remainder = insertAcross(
                    destination,
                    offered,
                    false,
                    snapshot.stacks().size());
            int accepted = acceptedCount(offered, remainder);
            if (accepted != plan.accepted()
                    || !snapshot.matchesAfterInsertion(destination, offered, accepted)) {
                return rollbackInsertion(destination, snapshot, offered);
            }
            return new InsertionResult(TransactionStatus.COMMITTED, accepted, remainder.copy());
        } catch (RuntimeException exception) {
            return rollbackInsertion(destination, snapshot, offered);
        }
    }

    public static TransferResult transfer(
            IItemHandlerModifiable source,
            int sourceSlot,
            IItemHandlerModifiable destination,
            int maximumItems) {
        if (source == null
                || destination == null
                || source == destination
                || sourceSlot < 0
                || maximumItems <= 0) {
            return TransferResult.invalid();
        }

        HandlerSnapshot sourceSnapshot = HandlerSnapshot.capture(source);
        HandlerSnapshot destinationSnapshot = HandlerSnapshot.capture(destination);
        if (sourceSnapshot == null
                || destinationSnapshot == null
                || sourceSlot >= sourceSnapshot.stacks().size()) {
            return TransferResult.invalid();
        }

        try {
            ItemStack simulatedExtraction = source.extractItem(sourceSlot, maximumItems, true);
            if (!sourceSnapshot.matches(source)) {
                return rollbackTransfer(
                        source,
                        sourceSnapshot,
                        destination,
                        destinationSnapshot);
            }
            if (simulatedExtraction.isEmpty()) {
                return TransferResult.noOp();
            }
            ItemStack originalSourceStack = sourceSnapshot.stacks().get(sourceSlot);
            if (originalSourceStack.isEmpty()
                    || simulatedExtraction.getCount() > maximumItems
                    || simulatedExtraction.getCount() > originalSourceStack.getCount()
                    || !ItemStack.isSameItemSameTags(originalSourceStack, simulatedExtraction)) {
                return TransferResult.invalid();
            }

            InsertionPlan destinationPlan = planInsertion(destination, simulatedExtraction);
            if (!destinationPlan.valid()) {
                if (destinationPlan.status() == PlanStatus.INVALID_HANDLER
                        && sourceSnapshot.matches(source)
                        && destinationSnapshot.matches(destination)) {
                    return TransferResult.invalid();
                }
                return rollbackTransfer(
                        source,
                        sourceSnapshot,
                        destination,
                        destinationSnapshot);
            }
            int accepted = destinationPlan.accepted();
            if (accepted <= 0) {
                return TransferResult.noOp();
            }

            ItemStack extracted = source.extractItem(sourceSlot, accepted, false);
            if (extracted.isEmpty()
                    || extracted.getCount() != accepted
                    || !ItemStack.isSameItemSameTags(simulatedExtraction, extracted)
                    || !sourceSnapshot.matchesAfterExtraction(
                            source,
                            sourceSlot,
                            extracted)) {
                return rollbackTransfer(
                        source,
                        sourceSnapshot,
                        destination,
                        destinationSnapshot);
            }

            ItemStack remainder = insertAcross(
                    destination,
                    extracted,
                    false,
                    destinationSnapshot.stacks().size());
            if (!remainder.isEmpty()
                    || !destinationSnapshot.matchesAfterInsertion(
                            destination,
                            extracted,
                            accepted)) {
                return rollbackTransfer(
                        source,
                        sourceSnapshot,
                        destination,
                        destinationSnapshot);
            }

            return new TransferResult(TransactionStatus.COMMITTED, accepted, extracted.copy());
        } catch (RuntimeException exception) {
            return rollbackTransfer(
                    source,
                    sourceSnapshot,
                    destination,
                    destinationSnapshot);
        }
    }

    private static InsertionResult rollbackInsertion(
            IItemHandlerModifiable destination,
            HandlerSnapshot snapshot,
            ItemStack offered) {
        boolean restored = snapshot.restoreSafely(destination);
        return new InsertionResult(
                restored ? TransactionStatus.ROLLED_BACK : TransactionStatus.ROLLBACK_FAILED,
                0,
                copyOrEmpty(offered));
    }

    private static TransferResult rollbackTransfer(
            IItemHandlerModifiable source,
            HandlerSnapshot sourceSnapshot,
            IItemHandlerModifiable destination,
            HandlerSnapshot destinationSnapshot) {
        boolean destinationRestored = destinationSnapshot.restoreSafely(destination);
        boolean sourceRestored = sourceSnapshot.restoreSafely(source);
        return new TransferResult(
                destinationRestored && sourceRestored
                        ? TransactionStatus.ROLLED_BACK
                        : TransactionStatus.ROLLBACK_FAILED,
                0,
                ItemStack.EMPTY);
    }

    private static ItemStack insertAcross(
            IItemHandlerModifiable handler,
            ItemStack offered,
            boolean simulate,
            int slots) {
        if (slots < 0 || slots > MAX_TRANSACTION_SLOTS) {
            throw new IllegalArgumentException("Transaction slot count is outside its bound");
        }
        ItemStack remainder = offered.copy();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            ItemStack before = remainder.copy();
            ItemStack response = handler.insertItem(slot, before.copy(), simulate);
            if (!validRemainder(before, response)) {
                throw new IllegalStateException("Item handler returned an invalid insertion remainder");
            }
            remainder = response.copy();
        }
        return remainder;
    }

    private static int acceptedCount(ItemStack offered, ItemStack remainder) {
        if (!validRemainder(offered, remainder)) {
            throw new IllegalStateException("Item handler changed the offered item during insertion");
        }
        return offered.getCount() - remainder.getCount();
    }

    private static boolean validRemainder(ItemStack offered, ItemStack remainder) {
        if (remainder == null) {
            return false;
        }
        if (remainder.isEmpty()) {
            return true;
        }
        return remainder.getCount() <= offered.getCount()
                && ItemStack.isSameItemSameTags(offered, remainder);
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public enum TransactionStatus {
        COMMITTED,
        NO_OP,
        INVALID_HANDLER,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    public enum PlanStatus {
        READY,
        INVALID_HANDLER,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    public record InsertionPlan(PlanStatus status, int accepted, ItemStack remainder) {
        public boolean valid() {
            return status == PlanStatus.READY;
        }

        private static InsertionPlan valid(ItemStack offered, ItemStack remainder) {
            return new InsertionPlan(
                    PlanStatus.READY,
                    acceptedCount(offered, remainder),
                    remainder.copy());
        }

        private static InsertionPlan invalid(ItemStack offered) {
            return failed(offered, PlanStatus.INVALID_HANDLER);
        }

        private static InsertionPlan failed(ItemStack offered, PlanStatus status) {
            return new InsertionPlan(status, 0, copyOrEmpty(offered));
        }
    }

    public record InsertionResult(
            TransactionStatus status,
            int inserted,
            ItemStack remainder) {
        public boolean committed() {
            return status == TransactionStatus.COMMITTED;
        }
    }

    public record TransferResult(
            TransactionStatus status,
            int transferred,
            ItemStack transferredStack) {
        public boolean committed() {
            return status == TransactionStatus.COMMITTED;
        }

        private static TransferResult invalid() {
            return new TransferResult(TransactionStatus.INVALID_HANDLER, 0, ItemStack.EMPTY);
        }

        private static TransferResult noOp() {
            return new TransferResult(TransactionStatus.NO_OP, 0, ItemStack.EMPTY);
        }
    }

    private static TransactionStatus transactionStatus(PlanStatus status) {
        return switch (status) {
            case ROLLED_BACK -> TransactionStatus.ROLLED_BACK;
            case ROLLBACK_FAILED -> TransactionStatus.ROLLBACK_FAILED;
            case READY, INVALID_HANDLER -> TransactionStatus.INVALID_HANDLER;
        };
    }

    private record HandlerSnapshot(List<ItemStack> stacks) {
        private static HandlerSnapshot capture(IItemHandlerModifiable handler) {
            try {
                int slots = handler.getSlots();
                if (slots < 0 || slots > MAX_TRANSACTION_SLOTS) {
                    return null;
                }
                List<ItemStack> stacks = new ArrayList<>(slots);
                for (int slot = 0; slot < slots; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack == null) {
                        return null;
                    }
                    stacks.add(stack.copy());
                }
                return new HandlerSnapshot(List.copyOf(stacks));
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private boolean matches(IItemHandlerModifiable handler) {
            try {
                if (handler.getSlots() != stacks.size()) {
                    return false;
                }
                for (int slot = 0; slot < stacks.size(); slot++) {
                    if (!stacksMatch(stacks.get(slot), handler.getStackInSlot(slot))) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private boolean matchesAfterExtraction(
                IItemHandlerModifiable handler,
                int sourceSlot,
                ItemStack extracted) {
            try {
                if (handler.getSlots() != stacks.size()) {
                    return false;
                }
                for (int slot = 0; slot < stacks.size(); slot++) {
                    ItemStack expected = stacks.get(slot).copy();
                    if (slot == sourceSlot) {
                        if (!ItemStack.isSameItemSameTags(expected, extracted)
                                || extracted.getCount() > expected.getCount()) {
                            return false;
                        }
                        expected.shrink(extracted.getCount());
                    }
                    if (!stacksMatch(expected, handler.getStackInSlot(slot))) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private boolean matchesAfterInsertion(
                IItemHandlerModifiable handler,
                ItemStack inserted,
                int insertedCount) {
            try {
                if (handler.getSlots() != stacks.size() || insertedCount <= 0) {
                    return false;
                }
                int observedIncrease = 0;
                for (int slot = 0; slot < stacks.size(); slot++) {
                    ItemStack before = stacks.get(slot);
                    ItemStack after = handler.getStackInSlot(slot);
                    if (before.isEmpty()) {
                        if (after.isEmpty()) {
                            continue;
                        }
                        if (!ItemStack.isSameItemSameTags(inserted, after)) {
                            return false;
                        }
                        observedIncrease = Math.addExact(observedIncrease, after.getCount());
                        continue;
                    }
                    if (!ItemStack.isSameItemSameTags(before, inserted)) {
                        if (!stacksMatch(before, after)) {
                            return false;
                        }
                        continue;
                    }
                    if (after.isEmpty()
                            || !ItemStack.isSameItemSameTags(before, after)
                            || after.getCount() < before.getCount()) {
                        return false;
                    }
                    observedIncrease = Math.addExact(
                            observedIncrease,
                            after.getCount() - before.getCount());
                }
                return observedIncrease == insertedCount;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private void restore(IItemHandlerModifiable handler) {
            if (handler.getSlots() != stacks.size()) {
                throw new IllegalStateException("Item handler slot count changed during transaction");
            }
            for (int slot = 0; slot < stacks.size(); slot++) {
                handler.setStackInSlot(slot, stacks.get(slot).copy());
            }
            if (!matches(handler)) {
                throw new IllegalStateException("Item handler rejected transaction rollback");
            }
        }

        private boolean restoreSafely(IItemHandlerModifiable handler) {
            try {
                restore(handler);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private static boolean stacksMatch(ItemStack first, ItemStack second) {
            if (first == null || second == null) {
                return false;
            }
            if (first.isEmpty() || second.isEmpty()) {
                return first.isEmpty() && second.isEmpty();
            }
            return first.getCount() == second.getCount()
                    && ItemStack.isSameItemSameTags(first, second);
        }
    }
}
