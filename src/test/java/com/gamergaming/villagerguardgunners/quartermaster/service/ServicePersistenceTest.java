package com.gamergaming.villagerguardgunners.quartermaster.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServicePersistenceTest {
    @TempDir Path directory;
    @Test void intentQuarantinesBothOldOwnersAfterRestart() {
        Path file = directory.resolve("journal.dat"); var journal = new ServiceJournal(file);
        var source = new ServiceReceipt(); var guard = new ServiceReceipt();
        assertTrue(journal.begin(source, guard));
        var restart = new ServiceJournal(file);
        assertFalse(restart.verify(source)); assertFalse(restart.verify(guard));
    }
    @Test void mixedSavesOnlyAcceptTheCurrentOwner() {
        Path file = directory.resolve("journal.dat"); var journal = new ServiceJournal(file);
        var source = new ServiceReceipt(); var guard = new ServiceReceipt();
        assertTrue(journal.begin(source, guard)); source.revision(1);
        var restart = new ServiceJournal(file);
        assertTrue(restart.verify(source)); assertFalse(restart.verify(guard));
        assertTrue(restart.recoverEmpty(guard)); assertTrue(restart.verify(guard));
    }
    @Test void successfulOwnerReceiptsSurviveRoundTrip() {
        var source = new ServiceReceipt(); var guard = new ServiceReceipt();
        Path file = directory.resolve("journal.dat"); assertTrue(new ServiceJournal(file).begin(source, guard));
        source.revision(1); guard.revision(1);
        CompoundTag saved = new CompoundTag(); source.save(saved);
        var loaded = new ServiceReceipt(source.id()); loaded.load(saved);
        assertTrue(new ServiceJournal(file).verify(loaded)); assertEquals(1, loaded.revision());
    }
    @Test void missingOldReceiptStillUsesStableOwnerIdentity() {
        Path file = directory.resolve("journal.dat"); var source = new ServiceReceipt();
        assertTrue(new ServiceJournal(file).begin(source, new ServiceReceipt()));
        var old = new ServiceReceipt(source.id()); old.load(new CompoundTag());
        assertFalse(new ServiceJournal(file).verify(old));
    }
    @Test void unknownReceiptIsPreservedAndCannotBeAcknowledged() {
        CompoundTag root = new CompoundTag(); CompoundTag raw = new CompoundTag(); raw.putInt("Version", 99); raw.putString("Evidence", "retain"); root.put(ServiceReceipt.KEY, raw);
        var receipt = new ServiceReceipt(); receipt.load(root);
        assertTrue(receipt.locked()); assertFalse(new ServiceJournal(directory.resolve("journal.dat")).recoverEmpty(receipt));
        CompoundTag saved = new CompoundTag(); receipt.save(saved); assertEquals(root, saved);
    }
    @Test void wrongOwnerReceiptCannotMigrateInventoryIdentity() {
        var receipt = new ServiceReceipt(); CompoundTag root = new CompoundTag(); receipt.save(root);
        var other = new ServiceReceipt(UUID.randomUUID()); other.load(root); assertTrue(other.locked());
    }
    @Test void malformedJournalAndWriteFailureFailClosed() throws Exception {
        Path malformed = directory.resolve("bad.dat"); Files.writeString(malformed, "not NBT");
        var broken = new ServiceJournal(malformed); assertFalse(broken.healthy()); assertFalse(broken.verify(new ServiceReceipt()));
        Path parentFile = directory.resolve("parent"); Files.writeString(parentFile, "not a directory");
        var unwritable = new ServiceJournal(parentFile.resolve("journal.dat"));
        assertFalse(unwritable.begin(new ServiceReceipt(), new ServiceReceipt())); assertFalse(unwritable.healthy());
    }
    @Test void duplicateOwnerCannotBeginAndDeletedJournalCannotAcceptReceipt() {
        var owner = new ServiceReceipt(); var journal = new ServiceJournal(directory.resolve("journal.dat"));
        assertFalse(journal.begin(owner, owner)); owner.revision(1); assertFalse(journal.verify(owner));
    }
}
