package android.example.judgement.ui;

import android.example.judgement.data.Records;
import android.example.judgement.data.RoundPlayerEdit;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoundEditRequestTest {
    @Test
    public void originalMetadataAndParticipantIdentityAreCopiedBeforeRecordsCanChange() {
        Fixture fixture = new Fixture();
        RoundEditRequest request = fixture.request();
        fixture.round.id = 900L;
        fixture.round.gameId = 800L;
        fixture.round.revision = 8L;
        fixture.round.scoringConfig = ScoringStrategy.defaults().encode();
        fixture.second.nameSnapshot = "Changed after editor opened";
        fixture.second.bid = 9;
        fixture.rows.clear();
        assertEquals(7L, request.gameId);
        assertEquals(77L, request.roundId);
        assertEquals(3L, request.expectedRevision);
        assertEquals(9, request.roundNumber);
        assertEquals(12, request.cards);
        assertEquals(22L, request.dealerId);
        assertEquals(ScoringStrategy.aggressive(), request.scoring);
        assertEquals(11L, request.players.get(0).playerId);
        assertEquals(22L, request.players.get(1).playerId);
        assertEquals("Second recorded name", request.player(22L).name);
        assertEquals(0, request.player(22L).originalBid);
        assertEquals(7L, request.originalPoints(22L));
        immutable(() -> request.players.clear());
    }

    @Test
    public void malformedRawTextSurvivesARealBundleParcelRoundTripWithoutBecomingZero() {
        RoundEditRequest request = new Fixture().request()
                .editBid(11L, " \n9223372036854775808000oops ")
                .editOutcome(22L, Outcome.LOST)
                .withError("This corrected allocation is impossible.");
        RoundEditRequest restored = parcel(request);
        assertEquals(request.token, restored.token);
        assertEquals(request.generation, restored.generation);
        assertEquals(request.gameId, restored.gameId);
        assertEquals(request.roundId, restored.roundId);
        assertEquals(request.expectedRevision, restored.expectedRevision);
        assertEquals(request.scoring, restored.scoring);
        assertEquals(" \n9223372036854775808000oops ", restored.player(11L).bidText);
        assertEquals(Outcome.LOST, restored.player(22L).outcome);
        assertEquals(Outcome.WON, restored.player(22L).originalOutcome);
        assertEquals("This corrected allocation is impossible.", restored.error);
        assertTrue(restored.validate);
        assertNotNull(restored.bidError(11L));
        rejects(restored::edits);
    }

    @Test
    public void generationTracksInputChangesWhileTokenAndOriginalRevisionStayFixed() {
        RoundEditRequest original = new Fixture().request();
        assertSame(original, original.editBid(11L, "7"));
        assertSame(original, original.editOutcome(11L, Outcome.WON));
        RoundEditRequest typed = original.editBid(11L, "8");
        RoundEditRequest picked = typed.editOutcome(11L, Outcome.LOST);
        assertEquals(0L, original.generation);
        assertEquals(1L, typed.generation);
        assertEquals(2L, picked.generation);
        assertEquals(original.token, picked.token);
        assertEquals(original.expectedRevision, picked.expectedRevision);
        assertEquals("7", original.player(11L).bidText);
        assertEquals(Outcome.WON, original.player(11L).outcome);
        assertNotEquals(original.token, new Fixture().request().token);
    }

    @Test
    public void validationShowsFieldErrorsWithoutDroppingAnyOtherEnteredValue() {
        RoundEditRequest original = new Fixture().request().editBid(11L, "999")
                .editBid(22L, " 0002 ");
        assertNull(original.bidError(11L));
        RoundEditRequest validated = original.validated();
        assertNotNull(validated.bidError(11L));
        assertNull(validated.bidError(22L));
        assertEquals("999", validated.player(11L).bidText);
        assertEquals(" 0002 ", validated.player(22L).bidText);
        RoundEditRequest repaired = validated.withError("Old allocation error").editBid(11L, "0");
        assertTrue(repaired.validate);
        assertNull(repaired.error);
        assertNull(repaired.bidError(11L));
        assertEquals(0, repaired.edits().get(0).bid);
        assertEquals(2, repaired.edits().get(1).bid);
    }

    @Test
    public void bidParsingRejectsMissingNegativeFractionalOverflowAndOutOfRangeInputs() {
        for (String raw : new String[] {"", " ", "-1", "+1", "1.5", "13", "2147483648",
                "9223372036854775807", "٢", "one", "1 2"}) {
            RoundEditRequest request = new Fixture().request().editBid(11L, raw).validated();
            assertEquals(raw, request.player(11L).bidText);
            assertNotNull("raw=" + raw, request.bidError(11L));
            rejects(request::edits);
        }
        RoundEditRequest boundaries = new Fixture().request().editBid(11L, "12")
                .editBid(22L, "0");
        assertEquals(12, boundaries.edits().get(0).bid);
        assertEquals(0, boundaries.edits().get(1).bid);
    }

    @Test
    public void missingOutcomeSurvivesRestorationAndNeedsAnExplicitChoice() {
        RoundEditRequest request = new Fixture().request().editOutcome(11L, null).validated();
        RoundEditRequest restored = parcel(request);
        assertNull(restored.player(11L).outcome);
        assertEquals(Outcome.WON, restored.player(11L).originalOutcome);
        assertNotNull(restored.outcomeError(11L));
        rejects(restored::edits);
        RoundEditRequest repaired = restored.editOutcome(11L, Outcome.LOST);
        assertNull(repaired.outcomeError(11L));
        assertEquals(Outcome.LOST, repaired.edits().get(0).outcome);
    }

    @Test
    public void repositoryReceivesTheCompleteProposalForCrossPlayerMathematicalValidation() {
        RoundEditRequest request = new Fixture().request().editBid(11L, "0")
                .editBid(22L, "0").editOutcome(11L, Outcome.WON)
                .editOutcome(22L, Outcome.WON).editOutcome(33L, Outcome.WON);
        // All-zero winners cannot account for twelve tricks. This layer sends both rows so the
        // repository can reject the allocation atomically rather than validating rows in isolation.
        List<RoundPlayerEdit> edits = request.edits();
        assertEquals(3, edits.size());
        assertEquals(11L, edits.get(0).playerId);
        assertEquals(22L, edits.get(1).playerId);
        assertEquals(0, edits.get(0).bid);
        assertEquals(Outcome.WON, edits.get(1).outcome);
        immutable(edits::clear);
    }

    @Test
    public void originalScoringAndNamesStayRecordedAfterCurrentInputsAndGameRulesChange() {
        Fixture fixture = new Fixture();
        String longName = "निरज 🎴 Long recorded participant with a complete name";
        fixture.first.nameSnapshot = longName;
        fixture.round.scoringConfig = ScoringStrategy.custom(31L, 4L, 3L, true).encode();
        RoundEditRequest request = fixture.request().editBid(11L, "1")
                .editOutcome(11L, Outcome.LOST);
        fixture.round.scoringConfig = ScoringStrategy.defaults().encode();
        fixture.first.nameSnapshot = "Renamed today";
        RoundEditRequest restored = parcel(request);
        assertEquals(longName, restored.player(11L).name);
        assertEquals(7, restored.player(11L).originalBid);
        assertEquals(Outcome.WON, restored.player(11L).originalOutcome);
        assertEquals(35L, restored.originalPoints(11L));
        assertEquals(31L, restored.originalPoints(22L));
        assertEquals("1", restored.player(11L).bidText);
        assertEquals(Outcome.LOST, restored.player(11L).outcome);
    }

    @Test
    public void unknownParticipantsAndInvalidOriginalSetsCannotTargetAnotherPlayer() {
        RoundEditRequest request = new Fixture().request();
        rejects(() -> request.editBid(999L, "0"));
        rejects(() -> request.editOutcome(999L, Outcome.WON));
        Fixture duplicate = new Fixture(); duplicate.second.playerId = 11L;
        rejects(duplicate::request);
        Fixture wrongRound = new Fixture(); wrongRound.second.roundId = 123L;
        rejects(wrongRound::request);
        Fixture partial = new Fixture(); partial.first.bid = null;
        rejects(partial::request);
        Fixture unfinished = new Fixture(); unfinished.round.phase = "BIDDING";
        rejects(unfinished::request);
    }

    @Test
    public void savedEditorVersionsAreExplicitAndNeverSilentlyRebasedOrReset() {
        RoundEditRequest original = new Fixture().request().editBid(11L, "invalid");
        assertNull(RoundEditRequest.fromBundle(null));
        Bundle unsupported = original.toBundle(); unsupported.putInt("version", 2);
        rejects(() -> RoundEditRequest.fromBundle(unsupported));
        Bundle missing = original.toBundle(); missing.remove("players");
        rejects(() -> RoundEditRequest.fromBundle(missing));
        RoundEditRequest restored = parcel(original);
        assertEquals(77L, restored.roundId);
        assertEquals(3L, restored.expectedRevision);
        assertEquals("invalid", restored.player(11L).bidText);
        assertFalse(restored.validate);
    }

    private static RoundEditRequest parcel(RoundEditRequest request) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(request.toBundle());
            parcel.setDataPosition(0);
            Bundle restored = parcel.readBundle(RoundEditRequest.class.getClassLoader());
            return RoundEditRequest.fromBundle(restored);
        } finally { parcel.recycle(); }
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); fail("Expected this invalid editor state to be rejected"); }
        catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void immutable(Runnable operation) {
        try { operation.run(); fail("Expected immutable editor data"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static final class Fixture {
        final Records.Round round = new Records.Round();
        final Records.RoundPlayer first = new Records.RoundPlayer();
        final Records.RoundPlayer second = new Records.RoundPlayer();
        final Records.RoundPlayer third = new Records.RoundPlayer();
        final List<Records.RoundPlayer> rows;

        Fixture() {
            round.id = 77L; round.gameId = 7L; round.revision = 3L; round.dealerId = 22L;
            round.number = 9; round.cards = 12; round.phase = "COMPLETE";
            round.scoringConfig = ScoringStrategy.aggressive().encode();
            first.roundId = 77L; first.playerId = 11L; first.bidPosition = 0;
            first.bid = 7; first.outcome = Outcome.WON.name(); first.nameSnapshot = "First recorded name";
            second.roundId = 77L; second.playerId = 22L; second.bidPosition = 1;
            second.bid = 0; second.outcome = Outcome.WON.name(); second.nameSnapshot = "Second recorded name";
            third.roundId = 77L; third.playerId = 33L; third.bidPosition = 2;
            third.bid = 0; third.outcome = Outcome.LOST.name(); third.nameSnapshot = "Third recorded name";
            rows = new ArrayList<>(Arrays.asList(second, third, first));
        }

        RoundEditRequest request() { return new RoundEditRequest(round, rows); }
    }
}
